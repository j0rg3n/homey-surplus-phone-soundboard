package com.soundboard.protocol

import com.google.gson.JsonParser
import com.soundboard.DoneReason
import com.soundboard.ErrorCode
import org.junit.Assert.*
import org.junit.Test

class MessageProtocolTest {

    private fun parseJson(s: String) = JsonParser.parseString(s).asJsonObject

    // --- serialize ---

    @Test
    fun `serialize ping produces correct JSON`() {
        val obj = parseJson(MessageProtocol.serialize(SoundboardMessage.Ping))
        assertEquals("ping", obj.get("type").asString)
    }

    @Test
    fun `serialize play includes all fields`() {
        val msg = SoundboardMessage.Play(soundId = "u1", volume = 150, handle = "h1")
        val obj = parseJson(MessageProtocol.serialize(msg))
        assertEquals("play", obj.get("type").asString)
        assertEquals("u1", obj.get("soundId").asString)
        assertEquals(150, obj.get("volume").asInt)
        assertEquals("h1", obj.get("handle").asString)
    }

    @Test
    fun `serialize hello_ack includes sounds array`() {
        val sound = SoundInfo("u1", "Doorbell", 2400L, false, 0L, 2400L, 100)
        val msg = SoundboardMessage.HelloAck("Galaxy A8", "1.0", listOf(sound))
        val obj = parseJson(MessageProtocol.serialize(msg))
        val sounds = obj.get("sounds").asJsonArray
        assertEquals(1, sounds.size())
        assertEquals("Doorbell", sounds[0].asJsonObject.get("name").asString)
    }

    @Test
    fun `serialize error with null handle emits null`() {
        val msg = SoundboardMessage.Error(handle = null, code = ErrorCode.SOUND_NOT_FOUND, message = "not found")
        val obj = parseJson(MessageProtocol.serialize(msg))
        assertTrue(obj.get("handle").isJsonNull)
    }

    // --- deserialize ---

    @Test
    fun `deserialize ping returns Ping`() {
        assertSame(SoundboardMessage.Ping, MessageProtocol.deserialize("""{"type":"ping"}"""))
    }

    @Test
    fun `deserialize pong returns Pong`() {
        assertSame(SoundboardMessage.Pong, MessageProtocol.deserialize("""{"type":"pong"}"""))
    }

    @Test
    fun `deserialize stop_all returns StopAll`() {
        assertSame(SoundboardMessage.StopAll, MessageProtocol.deserialize("""{"type":"stop_all"}"""))
    }

    @Test
    fun `deserialize play returns correct fields`() {
        val msg = MessageProtocol.deserialize("""{"type":"play","soundId":"u1","volume":100,"handle":"h1"}""") as SoundboardMessage.Play
        assertEquals("u1", msg.soundId)
        assertEquals(100, msg.volume)
        assertEquals("h1", msg.handle)
    }

    @Test
    fun `deserialize hello returns correct version`() {
        val msg = MessageProtocol.deserialize("""{"type":"hello","version":"1.0"}""") as SoundboardMessage.Hello
        assertEquals("1.0", msg.version)
    }

    @Test
    fun `deserialize hello_ack parses sounds list`() {
        val json = """{"type":"hello_ack","deviceName":"A8","version":"1.0","sounds":[{"id":"u1","name":"Beep","durationMs":500,"loop":false,"loopStartMs":0,"loopEndMs":500,"defaultVolume":100}]}"""
        val msg = MessageProtocol.deserialize(json) as SoundboardMessage.HelloAck
        assertEquals("A8", msg.deviceName)
        assertEquals(1, msg.sounds.size)
        assertEquals("Beep", msg.sounds[0].name)
    }

    @Test
    fun `deserialize started returns all fields`() {
        val json = """{"type":"started","handle":"h1","soundId":"u1","soundName":"Bell","durationMs":1200}"""
        val msg = MessageProtocol.deserialize(json) as SoundboardMessage.Started
        assertEquals("h1", msg.handle)
        assertEquals(1200L, msg.durationMs)
    }

    @Test
    fun `deserialize done returns reason`() {
        val json = """{"type":"done","handle":"h1","soundName":"Bell","reason":"completed"}"""
        val msg = MessageProtocol.deserialize(json) as SoundboardMessage.Done
        assertEquals(DoneReason.COMPLETED, msg.reason)
    }

    @Test
    fun `deserialize error with null handle`() {
        val json = """{"type":"error","handle":null,"code":"SOUND_NOT_FOUND","message":"not found"}"""
        val msg = MessageProtocol.deserialize(json) as SoundboardMessage.Error
        assertNull(msg.handle)
        assertEquals(ErrorCode.SOUND_NOT_FOUND, msg.code)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `deserialize throws on invalid JSON`() {
        MessageProtocol.deserialize("not json")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `deserialize throws on missing type`() {
        MessageProtocol.deserialize("""{"foo":"bar"}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `deserialize throws on unknown type`() {
        MessageProtocol.deserialize("""{"type":"bogus"}""")
    }

    @Test
    fun `serialize then deserialize round-trips play message`() {
        val original = SoundboardMessage.Play(soundId = "abc", volume = 200, handle = "xyz")
        assertEquals(original, MessageProtocol.deserialize(MessageProtocol.serialize(original)))
    }

    @Test
    fun `serialize then deserialize round-trips done message`() {
        val original = SoundboardMessage.Done(handle = "h2", soundName = "Clap", reason = DoneReason.STOPPED)
        assertEquals(original, MessageProtocol.deserialize(MessageProtocol.serialize(original)))
    }

    // --- validate ---

    @Test
    fun `validate returns invalid for bad JSON`() {
        val r = MessageProtocol.validate("not json")
        assertFalse(r.valid)
        assertTrue(r.errors.any { it.contains("Invalid JSON") })
    }

    @Test
    fun `validate returns invalid when type missing`() {
        val r = MessageProtocol.validate("""{"foo":"bar"}""")
        assertFalse(r.valid)
        assertTrue(r.errors.any { it.contains("type") })
    }

    @Test
    fun `validate returns invalid for unknown type`() {
        val r = MessageProtocol.validate("""{"type":"bogus"}""")
        assertFalse(r.valid)
        assertTrue(r.errors.any { it.contains("Unknown") })
    }

    @Test
    fun `validate ping is valid`() {
        assertTrue(MessageProtocol.validate("""{"type":"ping"}""").valid)
    }

    @Test
    fun `validate stop_all is valid`() {
        assertTrue(MessageProtocol.validate("""{"type":"stop_all"}""").valid)
    }

    @Test
    fun `validate play is valid with all fields`() {
        assertTrue(MessageProtocol.validate("""{"type":"play","soundId":"u1","volume":100,"handle":"h1"}""").valid)
    }

    @Test
    fun `validate play reports missing handle`() {
        val r = MessageProtocol.validate("""{"type":"play","soundId":"u1","volume":100}""")
        assertFalse(r.valid)
        assertTrue(r.errors.any { it.contains("handle") })
    }

    @Test
    fun `validate play reports missing soundId`() {
        val r = MessageProtocol.validate("""{"type":"play","volume":100,"handle":"h1"}""")
        assertFalse(r.valid)
        assertTrue(r.errors.any { it.contains("soundId") })
    }

    @Test
    fun `validate hello_ack valid`() {
        assertTrue(MessageProtocol.validate("""{"type":"hello_ack","deviceName":"A8","version":"1.0","sounds":[]}""").valid)
    }

    @Test
    fun `validate hello_ack missing deviceName`() {
        val r = MessageProtocol.validate("""{"type":"hello_ack","version":"1.0","sounds":[]}""")
        assertFalse(r.valid)
        assertTrue(r.errors.any { it.contains("deviceName") })
    }

    @Test
    fun `validate started valid`() {
        assertTrue(MessageProtocol.validate("""{"type":"started","handle":"h1","soundId":"u1","soundName":"Bell","durationMs":1000}""").valid)
    }

    @Test
    fun `validate started missing durationMs`() {
        val r = MessageProtocol.validate("""{"type":"started","handle":"h1","soundId":"u1","soundName":"Bell"}""")
        assertFalse(r.valid)
        assertTrue(r.errors.any { it.contains("durationMs") })
    }

    @Test
    fun `validate done valid`() {
        assertTrue(MessageProtocol.validate("""{"type":"done","handle":"h1","soundName":"Bell","reason":"completed"}""").valid)
    }

    @Test
    fun `validate error with null handle is valid`() {
        assertTrue(MessageProtocol.validate("""{"type":"error","handle":null,"code":"SOUND_NOT_FOUND","message":"oops"}""").valid)
    }

    @Test
    fun `validate error missing code is invalid`() {
        val r = MessageProtocol.validate("""{"type":"error","handle":null,"message":"oops"}""")
        assertFalse(r.valid)
        assertTrue(r.errors.any { it.contains("code") })
    }
}
