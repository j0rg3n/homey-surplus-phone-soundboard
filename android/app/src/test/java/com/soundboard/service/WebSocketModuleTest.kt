package com.soundboard.service

import com.soundboard.ErrorCode
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

    /** Receive the next Text frame, skipping non-text frames, with a 2-second timeout. */
    private suspend fun ReceiveChannel<Frame>.receiveText(): String = withTimeout(2_000) {
        var frame: Frame
        do {
            frame = receive()
        } while (frame !is Frame.Text)
        (frame as Frame.Text).readText()
    }

    /** Perform the hello/hello_ack handshake and return the HelloAck message. */
    private suspend fun io.ktor.client.plugins.websocket.ClientWebSocketSession.doHello(): SoundboardMessage.HelloAck {
        send(Frame.Text(MessageProtocol.serialize(SoundboardMessage.Hello("1.0"))))
        val msg = MessageProtocol.deserialize(incoming.receiveText())
        assertTrue("Expected HelloAck, got $msg", msg is SoundboardMessage.HelloAck)
        return msg as SoundboardMessage.HelloAck
    }

    // ------------------------------------------------------------------ tests

    @Test
    fun `hello handshake returns hello_ack`() = testApplication {
        val engine = AudioEngine()
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
    fun `play returns started response after handshake`() = testApplication {
        val engine = AudioEngine()
        val registry = SessionRegistry()
        application { soundboardWebSocketModule(engine, registry, deviceName = "TestDevice") }

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
    fun `stop after play returns done with reason stopped`() = testApplication {
        val engine = AudioEngine()
        val registry = SessionRegistry()
        application { soundboardWebSocketModule(engine, registry, deviceName = "TestDevice") }

        val client = createClient { install(WebSockets) }
        client.webSocket("/") {
            doHello()

            // Play a sound
            send(Frame.Text(MessageProtocol.serialize(
                SoundboardMessage.Play(soundId = "snd1", volume = 80, handle = "h2")
            )))
            // Consume the Started frame
            val started = MessageProtocol.deserialize(incoming.receiveText())
            assertTrue(started is SoundboardMessage.Started)

            // Stop it
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
        val engine = AudioEngine()
        val registry = SessionRegistry()
        application { soundboardWebSocketModule(engine, registry, deviceName = "TestDevice") }

        val client = createClient { install(WebSockets) }
        client.webSocket("/") {
            doHello()

            // Play two sounds simultaneously
            send(Frame.Text(MessageProtocol.serialize(
                SoundboardMessage.Play(soundId = "a", volume = 100, handle = "ha")
            )))
            send(Frame.Text(MessageProtocol.serialize(
                SoundboardMessage.Play(soundId = "b", volume = 100, handle = "hb")
            )))
            // Consume two Started frames
            val s1 = MessageProtocol.deserialize(incoming.receiveText())
            val s2 = MessageProtocol.deserialize(incoming.receiveText())
            assertTrue(s1 is SoundboardMessage.Started)
            assertTrue(s2 is SoundboardMessage.Started)

            // Stop all
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
        val engine = AudioEngine()
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
        val engine = AudioEngine()
        val registry = SessionRegistry()
        application { soundboardWebSocketModule(engine, registry, deviceName = "TestDevice") }

        val client = createClient { install(WebSockets) }
        client.webSocket("/") {
            doHello()
            // Send raw JSON with an unknown type (not in the protocol)
            send(Frame.Text("""{"type":"definitely_not_a_real_type"}"""))
            val msg = MessageProtocol.deserialize(incoming.receiveText())
            assertTrue("Expected Error, got $msg", msg is SoundboardMessage.Error)
            assertEquals(ErrorCode.UNKNOWN_MESSAGE, (msg as SoundboardMessage.Error).code)
        }
    }

    @Test
    fun `library_update broadcast reaches connected session`() = testApplication {
        val engine = AudioEngine()
        val registry = SessionRegistry()
        application { soundboardWebSocketModule(engine, registry, deviceName = "TestDevice") }

        val client = createClient { install(WebSockets) }
        val sounds = listOf(
            SoundInfo(id = "s1", name = "Bell", durationMs = 1000L,
                loop = false, loopStartMs = 0L, loopEndMs = 1000L, defaultVolume = 100)
        )

        client.webSocket("/") {
            doHello()

            // Session is now in the registry — broadcast
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
        val engine = AudioEngine()
        val registry = SessionRegistry()
        application { soundboardWebSocketModule(engine, registry, deviceName = "TestDevice") }

        val client = createClient { install(WebSockets) }
        client.webSocket("/") {
            doHello()
            assertEquals(1, registry.all().size)
        }
        // After block ends the session closes; give the server a moment to remove it
        withTimeout(500) {
            while (registry.all().isNotEmpty()) kotlinx.coroutines.delay(10)
        }
        assertEquals(0, registry.all().size)
    }
}
