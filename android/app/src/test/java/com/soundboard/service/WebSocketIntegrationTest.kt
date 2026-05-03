package com.soundboard.service

import com.soundboard.DoneReason
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

/**
 * Integration smoke test for the WebSocket session lifecycle (TODO 7.2).
 *
 * Uses Ktor's testApplication engine — no real network socket or device needed.
 * All players are controlled via [ControllableFakePlayer] so tests can trigger
 * natural completion or leave sounds in-flight at disconnect.
 */
@RunWith(JUnit4::class)
class WebSocketIntegrationTest {

    // ------------------------------------------------------------------ helpers

    private val players = mutableListOf<ControllableFakePlayer>()

    private fun controllableEngine(): AudioEngine {
        players.clear()
        return AudioEngine { ControllableFakePlayer().also { players += it } }
    }

    private fun fakeSample(id: String) = SampleEntity(
        id = id, name = "Sound $id", filePath = "/fake/$id.mp3",
        durationMs = 3000L, defaultVolume = 100, loop = false,
        loopStartMs = 0L, loopEndMs = 3000L, createdAt = 0L,
    )

    private val sounds = listOf(
        SoundInfo(id = "snd1", name = "Sound snd1", durationMs = 3000L,
            loop = false, loopStartMs = 0L, loopEndMs = 3000L, defaultVolume = 100),
        SoundInfo(id = "snd2", name = "Sound snd2", durationMs = 3000L,
            loop = false, loopStartMs = 0L, loopEndMs = 3000L, defaultVolume = 100),
        SoundInfo(id = "snd3", name = "Sound snd3", durationMs = 3000L,
            loop = false, loopStartMs = 0L, loopEndMs = 3000L, defaultVolume = 100),
    )

    private suspend fun ReceiveChannel<Frame>.receiveText(): String = withTimeout(2_000) {
        var frame: Frame
        do { frame = receive() } while (frame !is Frame.Text)
        (frame as Frame.Text).readText()
    }

    private suspend fun io.ktor.client.plugins.websocket.ClientWebSocketSession.doHello(
        incoming: ReceiveChannel<Frame>,
    ): SoundboardMessage.HelloAck {
        send(Frame.Text(MessageProtocol.serialize(SoundboardMessage.Hello("1.0"))))
        val msg = MessageProtocol.deserialize(incoming.receiveText())
        assertTrue("Expected HelloAck, got $msg", msg is SoundboardMessage.HelloAck)
        return msg as SoundboardMessage.HelloAck
    }

    private fun playFrame(soundId: String, handle: String) =
        Frame.Text(MessageProtocol.serialize(
            SoundboardMessage.Play(soundId = soundId, volume = 100, handle = handle)
        ))

    private fun stopFrame(handle: String) =
        Frame.Text(MessageProtocol.serialize(SoundboardMessage.Stop(handle = handle)))

    // ------------------------------------------------------------------ tests

    /**
     * Full lifecycle smoke test:
     *  1. hello → hello_ack (with sound library)
     *  2. play h1, h2, h3 simultaneously → receive started for all 3
     *  3. stop h1 → done(reason=stopped, handle=h1)
     *  4. natural completion of h2 → done(reason=completed, handle=h2)
     *  5. h3 still in-flight when connection closes → connection_lost fires (internally)
     *  6. total done messages received by client ≥ 2 (stopped + completed)
     */
    @Test
    fun `full lifecycle - hello, three simultaneous plays, stop, completion, and connection_lost`() =
        testApplication {
            val engine = controllableEngine()
            val registry = SessionRegistry()
            application {
                soundboardWebSocketModule(
                    audioEngine = engine,
                    registry = registry,
                    deviceName = "IntegrationDevice",
                    sounds = { sounds },
                    lookupSound = { id -> fakeSample(id) },
                )
            }

            val client = createClient { install(WebSockets) }
            val doneMessages = mutableListOf<SoundboardMessage.Done>()

            client.webSocket("/") {
                // Step 1: hello → hello_ack with sound library
                val ack = doHello(incoming)
                assertEquals("IntegrationDevice", ack.deviceName)
                assertEquals("1.0", ack.version)
                assertEquals(3, ack.sounds.size)

                // Step 2: play h1, h2, h3 simultaneously
                send(playFrame("snd1", "h1"))
                send(playFrame("snd2", "h2"))
                send(playFrame("snd3", "h3"))

                // Collect started messages (order not guaranteed)
                val startedHandles = mutableSetOf<String>()
                repeat(3) {
                    val msg = MessageProtocol.deserialize(incoming.receiveText())
                    assertTrue("Expected Started, got $msg", msg is SoundboardMessage.Started)
                    startedHandles += (msg as SoundboardMessage.Started).handle
                }
                assertEquals("All three handles should have started",
                    setOf("h1", "h2", "h3"), startedHandles)

                // Step 3: stop h1 → done(stopped, h1)
                send(stopFrame("h1"))
                val doneH1msg = MessageProtocol.deserialize(incoming.receiveText())
                assertTrue("Expected Done for h1, got $doneH1msg", doneH1msg is SoundboardMessage.Done)
                val doneH1 = doneH1msg as SoundboardMessage.Done
                assertEquals("h1", doneH1.handle)
                assertEquals(DoneReason.STOPPED, doneH1.reason)
                doneMessages += doneH1

                // Step 4: trigger natural completion for h2
                // players list is ordered by play call: index 0=h1, 1=h2, 2=h3
                val playerH2 = players[1]
                playerH2.triggerCompletion()
                val doneH2msg = MessageProtocol.deserialize(incoming.receiveText())
                assertTrue("Expected Done for h2, got $doneH2msg", doneH2msg is SoundboardMessage.Done)
                val doneH2 = doneH2msg as SoundboardMessage.Done
                assertEquals("h2", doneH2.handle)
                assertEquals(DoneReason.COMPLETED, doneH2.reason)
                doneMessages += doneH2

                // Step 5: close connection with h3 still in-flight
                // (connection_lost is fired in the server's finally block — client won't
                // receive it because the socket is already closing, but we verify it
                // was triggered by checking the engine state after disconnect)
            }

            // Step 6: verify at least 2 done messages were received by the client
            assertEquals("Expected 2 done messages (stopped + completed) received by client",
                2, doneMessages.size)

            val doneReasons = doneMessages.map { it.reason }.toSet()
            assertTrue("stopped reason missing", DoneReason.STOPPED in doneReasons)
            assertTrue("completed reason missing", DoneReason.COMPLETED in doneReasons)

            // Step 7: verify h3 was cleaned up (engine fired connection_lost internally)
            // Wait briefly for the server's finally block to run after the client disconnects
            withTimeout(1_000) {
                while (engine.activeHandles().isNotEmpty()) kotlinx.coroutines.delay(10)
            }
            assertTrue("h3 should have been cleaned up by connection_lost",
                engine.activeHandles().isEmpty())
        }

    @Test
    fun `hello_ack contains the full sound library`() = testApplication {
        val engine = controllableEngine()
        val registry = SessionRegistry()
        application {
            soundboardWebSocketModule(
                audioEngine = engine,
                registry = registry,
                deviceName = "LibraryDevice",
                sounds = { sounds },
            )
        }

        val client = createClient { install(WebSockets) }
        client.webSocket("/") {
            val ack = doHello(incoming)
            assertEquals("LibraryDevice", ack.deviceName)
            assertEquals(3, ack.sounds.size)
            val ids = ack.sounds.map { it.id }.toSet()
            assertEquals(setOf("snd1", "snd2", "snd3"), ids)
        }
    }

    @Test
    fun `three simultaneous plays all receive started`() = testApplication {
        val engine = controllableEngine()
        val registry = SessionRegistry()
        application {
            soundboardWebSocketModule(
                audioEngine = engine,
                registry = registry,
                lookupSound = { id -> fakeSample(id) },
            )
        }

        val client = createClient { install(WebSockets) }
        client.webSocket("/") {
            doHello(incoming)
            send(playFrame("snd1", "h1"))
            send(playFrame("snd2", "h2"))
            send(playFrame("snd3", "h3"))

            val handles = mutableSetOf<String>()
            repeat(3) {
                val msg = MessageProtocol.deserialize(incoming.receiveText())
                assertTrue("Expected Started, got $msg", msg is SoundboardMessage.Started)
                handles += (msg as SoundboardMessage.Started).handle
            }
            assertEquals(setOf("h1", "h2", "h3"), handles)
        }
    }

    @Test
    fun `stop h1 while h2 and h3 still playing returns done with reason stopped for h1 only`() =
        testApplication {
            val engine = controllableEngine()
            val registry = SessionRegistry()
            application {
                soundboardWebSocketModule(
                    audioEngine = engine,
                    registry = registry,
                    lookupSound = { id -> fakeSample(id) },
                )
            }

            val client = createClient { install(WebSockets) }
            client.webSocket("/") {
                doHello(incoming)
                send(playFrame("snd1", "h1"))
                send(playFrame("snd2", "h2"))
                send(playFrame("snd3", "h3"))
                repeat(3) { incoming.receiveText() } // drain started frames

                send(stopFrame("h1"))
                val done = MessageProtocol.deserialize(incoming.receiveText())
                assertTrue("Expected Done, got $done", done is SoundboardMessage.Done)
                assertEquals("h1", (done as SoundboardMessage.Done).handle)
                assertEquals(DoneReason.STOPPED, done.reason)

                // h2 and h3 still active
                val active = engine.activeHandles()
                assertTrue("h2 should still be active", "h2" in active)
                assertTrue("h3 should still be active", "h3" in active)
            }
        }

    @Test
    fun `natural completion of h2 sends done with reason completed`() = testApplication {
        val engine = controllableEngine()
        val registry = SessionRegistry()
        application {
            soundboardWebSocketModule(
                audioEngine = engine,
                registry = registry,
                lookupSound = { id -> fakeSample(id) },
            )
        }

        val client = createClient { install(WebSockets) }
        client.webSocket("/") {
            doHello(incoming)
            send(playFrame("snd1", "h1"))
            send(playFrame("snd2", "h2"))
            send(playFrame("snd3", "h3"))
            repeat(3) { incoming.receiveText() } // drain started frames

            // trigger completion on h2 (index 1)
            players[1].triggerCompletion()

            val done = MessageProtocol.deserialize(incoming.receiveText())
            assertTrue("Expected Done, got $done", done is SoundboardMessage.Done)
            assertEquals("h2", (done as SoundboardMessage.Done).handle)
            assertEquals(DoneReason.COMPLETED, done.reason)
        }
    }

    @Test
    fun `h3 in-flight at disconnect is cleaned up with connection_lost`() = testApplication {
        val engine = controllableEngine()
        val registry = SessionRegistry()
        application {
            soundboardWebSocketModule(
                audioEngine = engine,
                registry = registry,
                lookupSound = { id -> fakeSample(id) },
            )
        }

        val client = createClient { install(WebSockets) }
        client.webSocket("/") {
            doHello(incoming)
            send(playFrame("snd1", "h1"))
            send(playFrame("snd2", "h2"))
            send(playFrame("snd3", "h3"))
            repeat(3) { incoming.receiveText() } // drain started frames

            // stop h1 and complete h2 — leave h3 in-flight
            send(stopFrame("h1"))
            incoming.receiveText() // done for h1
            players[1].triggerCompletion()
            incoming.receiveText() // done for h2

            // disconnect with h3 still active
        }

        // After disconnect the server finally-block calls fireConnectionLost()
        withTimeout(1_000) {
            while (engine.activeHandles().isNotEmpty()) kotlinx.coroutines.delay(10)
        }
        assertTrue("h3 should be cleaned up after connection close",
            engine.activeHandles().isEmpty())
    }
}

/**
 * A [Player] implementation whose completion callback can be triggered manually,
 * allowing integration tests to simulate natural end-of-media without a real audio device.
 */
private class ControllableFakePlayer : Player {
    private var completionCb: (() -> Unit)? = null

    override fun prepare(filePath: String, volume: Float, loop: Boolean) {}
    override fun start() {}
    override fun stop() {}
    override fun release() {}
    override fun setVolume(volume: Float) {}
    override fun setOnCompletionListener(cb: () -> Unit) { completionCb = cb }
    override fun setOnErrorListener(cb: (Int, Int) -> Boolean) {}

    /** Simulate the underlying media reaching its natural end. */
    fun triggerCompletion() { completionCb?.invoke() }
}
