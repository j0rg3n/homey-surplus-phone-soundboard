package com.soundboard.service

import com.soundboard.ErrorCode
import com.soundboard.data.SampleEntity
import com.soundboard.protocol.MessageProtocol
import com.soundboard.protocol.SoundInfo
import com.soundboard.protocol.SoundboardMessage
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class WebSocketModuleTest {

    // ------------------------------------------------------------------ helpers

    private fun fakeEngine() = AudioEngine { object : Player {
        override fun prepare(filePath: String, volume: Float, loop: Boolean) {}
        override fun start() {}
        override fun stop() {}
        override fun release() {}
        override fun setVolume(volume: Float) {}
        override fun setOnCompletionListener(cb: () -> Unit) {}
        override fun setOnErrorListener(cb: (Int, Int) -> Boolean) {}
    }}

    private fun fakeSample(id: String = "snd1") = SampleEntity(
        id = id, name = "Test Sound", filePath = "/fake/$id.mp3",
        durationMs = 1000L, defaultVolume = 100, loop = false,
        loopStartMs = 0L, loopEndMs = 1000L, createdAt = 0L,
    )

    private suspend fun ReceiveChannel<Frame>.receiveText(): String = withTimeout(2_000) {
        var frame: Frame
        do { frame = receive() } while (frame !is Frame.Text)
        (frame as Frame.Text).readText()
    }

    private suspend fun io.ktor.client.plugins.websocket.ClientWebSocketSession.doHello(): SoundboardMessage.HelloAck {
        send(Frame.Text(MessageProtocol.serialize(SoundboardMessage.Hello("1.0"))))
        val msg = MessageProtocol.deserialize(incoming.receiveText())
        assertTrue("Expected HelloAck, got $msg", msg is SoundboardMessage.HelloAck)
        return msg as SoundboardMessage.HelloAck
    }

    // ------------------------------------------------------------------ tests

    @Test
    fun `hello handshake returns hello_ack`() = testApplication {
        val engine = fakeEngine()
        val registry = SessionRegistry()
        application { soundboardWebSocketModule(engine, registry, deviceName = "TestDevice") }

        val client = createClient { install(WebSockets) }
        client.webSocket("/") {
            val ack = doHello()
            assertEquals("TestDevice", ack.deviceName)
            assertEquals("1.0", ack.version)
            assertTrue(ack.sounds.isEmpty())
        }
    }

    @Test
    fun `hello_ack includes sound library`() = testApplication {
        val sounds = listOf(
            SoundInfo(id = "s1", name = "Bell", durationMs = 1000L,
                loop = false, loopStartMs = 0L, loopEndMs = 1000L, defaultVolume = 100)
        )
        val engine = fakeEngine()
        val registry = SessionRegistry()
        application { soundboardWebSocketModule(engine, registry, sounds = { sounds }) }

        val client = createClient { install(WebSockets) }
        client.webSocket("/") {
            val ack = doHello()
            assertEquals(1, ack.sounds.size)
            assertEquals("Bell", ack.sounds[0].name)
        }
    }

    @Test
    fun `play returns started response after handshake`() = testApplication {
        val engine = fakeEngine()
        val registry = SessionRegistry()
        application { soundboardWebSocketModule(engine, registry, deviceName = "TestDevice",
            lookupSound = { id -> fakeSample(id) }) }

        val client = createClient { install(WebSockets) }
        client.webSocket("/") {
            doHello()
            send(Frame.Text(MessageProtocol.serialize(
                SoundboardMessage.Play(soundId = "snd1", volume = 100, handle = "h1")
            )))
            val msg = MessageProtocol.deserialize(incoming.receiveText())
            assertTrue("Expected Started, got $msg", msg is SoundboardMessage.Started)
            val started = msg as SoundboardMessage.Started
            assertEquals("h1", started.handle)
            assertEquals("snd1", started.soundId)
        }
    }

    @Test
    fun `play returns error when sound not found`() = testApplication {
        val engine = fakeEngine()
        val registry = SessionRegistry()
        application { soundboardWebSocketModule(engine, registry) }

        val client = createClient { install(WebSockets) }
        client.webSocket("/") {
            doHello()
            send(Frame.Text(MessageProtocol.serialize(
                SoundboardMessage.Play(soundId = "missing", volume = 100, handle = "h1")
            )))
            val msg = MessageProtocol.deserialize(incoming.receiveText())
            assertTrue("Expected Error, got $msg", msg is SoundboardMessage.Error)
            assertEquals(ErrorCode.SOUND_NOT_FOUND, (msg as SoundboardMessage.Error).code)
        }
    }

    @Test
    fun `stop after play returns done with reason stopped`() = testApplication {
        val players = mutableListOf<StoppablePlayer>()
        val engine = AudioEngine {
            StoppablePlayer().also { players += it }
        }
        val registry = SessionRegistry()
        application { soundboardWebSocketModule(engine, registry, deviceName = "TestDevice",
            lookupSound = { id -> fakeSample(id) }) }

        val client = createClient { install(WebSockets) }
        client.webSocket("/") {
            doHello()
            send(Frame.Text(MessageProtocol.serialize(
                SoundboardMessage.Play(soundId = "snd1", volume = 80, handle = "h2")
            )))
            val started = MessageProtocol.deserialize(incoming.receiveText())
            assertTrue(started is SoundboardMessage.Started)

            send(Frame.Text(MessageProtocol.serialize(SoundboardMessage.Stop(handle = "h2"))))
            val msg = MessageProtocol.deserialize(incoming.receiveText())
            assertTrue("Expected Done, got $msg", msg is SoundboardMessage.Done)
            val done = msg as SoundboardMessage.Done
            assertEquals("h2", done.handle)
            assertEquals("stopped", done.reason)
        }
    }

    @Test
    fun `stop_all returns done for all in-flight handles`() = testApplication {
        val engine = AudioEngine {
            StoppablePlayer()
        }
        val registry = SessionRegistry()
        application { soundboardWebSocketModule(engine, registry, deviceName = "TestDevice",
            lookupSound = { id -> fakeSample(id) }) }

        val client = createClient { install(WebSockets) }
        client.webSocket("/") {
            doHello()

            send(Frame.Text(MessageProtocol.serialize(
                SoundboardMessage.Play(soundId = "a", volume = 100, handle = "ha")
            )))
            send(Frame.Text(MessageProtocol.serialize(
                SoundboardMessage.Play(soundId = "b", volume = 100, handle = "hb")
            )))
            val s1 = MessageProtocol.deserialize(incoming.receiveText())
            val s2 = MessageProtocol.deserialize(incoming.receiveText())
            assertTrue(s1 is SoundboardMessage.Started)
            assertTrue(s2 is SoundboardMessage.Started)

            send(Frame.Text(MessageProtocol.serialize(SoundboardMessage.StopAll)))
            val d1 = MessageProtocol.deserialize(incoming.receiveText())
            val d2 = MessageProtocol.deserialize(incoming.receiveText())
            assertTrue("Expected Done (1), got $d1", d1 is SoundboardMessage.Done)
            assertTrue("Expected Done (2), got $d2", d2 is SoundboardMessage.Done)

            val handles = setOf((d1 as SoundboardMessage.Done).handle, (d2 as SoundboardMessage.Done).handle)
            assertEquals(setOf("ha", "hb"), handles)
            assertEquals("stopped", d1.reason)
            assertEquals("stopped", d2.reason)
        }
    }

    @Test
    fun `ping returns pong`() = testApplication {
        val engine = fakeEngine()
        val registry = SessionRegistry()
        application { soundboardWebSocketModule(engine, registry, deviceName = "TestDevice") }

        val client = createClient { install(WebSockets) }
        client.webSocket("/") {
            doHello()
            send(Frame.Text(MessageProtocol.serialize(SoundboardMessage.Ping)))
            val msg = MessageProtocol.deserialize(incoming.receiveText())
            assertTrue("Expected Pong, got $msg", msg is SoundboardMessage.Pong)
        }
    }

    @Test
    fun `unknown message type returns error with UNKNOWN_MESSAGE code`() = testApplication {
        val engine = fakeEngine()
        val registry = SessionRegistry()
        application { soundboardWebSocketModule(engine, registry, deviceName = "TestDevice") }

        val client = createClient { install(WebSockets) }
        client.webSocket("/") {
            doHello()
            send(Frame.Text("""{"type":"definitely_not_a_real_type"}"""))
            val msg = MessageProtocol.deserialize(incoming.receiveText())
            assertTrue("Expected Error, got $msg", msg is SoundboardMessage.Error)
            assertEquals(ErrorCode.UNKNOWN_MESSAGE, (msg as SoundboardMessage.Error).code)
        }
    }

    @Test
    fun `library_update broadcast reaches connected session`() = testApplication {
        val engine = fakeEngine()
        val registry = SessionRegistry()
        application { soundboardWebSocketModule(engine, registry, deviceName = "TestDevice") }

        val client = createClient { install(WebSockets) }
        val sounds = listOf(
            SoundInfo(id = "s1", name = "Bell", durationMs = 1000L,
                loop = false, loopStartMs = 0L, loopEndMs = 1000L, defaultVolume = 100)
        )

        client.webSocket("/") {
            doHello()

            val broadcastFrame = Frame.Text(
                MessageProtocol.serialize(SoundboardMessage.LibraryUpdate(sounds))
            )
            registry.all().forEach { it.send(broadcastFrame) }

            val msg = MessageProtocol.deserialize(incoming.receiveText())
            assertTrue("Expected LibraryUpdate, got $msg", msg is SoundboardMessage.LibraryUpdate)
            assertEquals("Bell", (msg as SoundboardMessage.LibraryUpdate).sounds[0].name)
        }
    }

    @Test
    fun `session registry tracks and removes sessions`() = testApplication {
        val engine = fakeEngine()
        val registry = SessionRegistry()
        application { soundboardWebSocketModule(engine, registry, deviceName = "TestDevice") }

        val client = createClient { install(WebSockets) }
        client.webSocket("/") {
            doHello()
            assertEquals(1, registry.all().size)
        }
        withTimeout(500) {
            while (registry.all().isNotEmpty()) kotlinx.coroutines.delay(10)
        }
        assertEquals(0, registry.all().size)
    }
}

// Minimal player for stop/stop_all tests — stop() does NOT fire the completion callback
// (mirrors real MediaPlayer behaviour: completion only fires on natural end of media)
private class StoppablePlayer : Player {
    override fun prepare(filePath: String, volume: Float, loop: Boolean) {}
    override fun start() {}
    override fun stop() {}
    override fun release() {}
    override fun setVolume(volume: Float) {}
    override fun setOnCompletionListener(cb: () -> Unit) {}
    override fun setOnErrorListener(cb: (Int, Int) -> Boolean) {}
}
