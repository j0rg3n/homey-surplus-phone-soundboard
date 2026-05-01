package com.soundboard.service

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.pow

private class FakePlayer : Player {
    var prepareCalled = false
    var startCalled = false
    var stopCalled = false
    var releaseCalled = false
    var completionCb: (() -> Unit)? = null
    var errorCb: ((Int, Int) -> Boolean)? = null

    override fun prepare(filePath: String, volume: Float, loop: Boolean) { prepareCalled = true }
    override fun start() { startCalled = true }
    override fun stop() { stopCalled = true }
    override fun release() { releaseCalled = true }
    override fun setOnCompletionListener(cb: () -> Unit) { completionCb = cb }
    override fun setOnErrorListener(cb: (Int, Int) -> Boolean) { errorCb = cb }

    fun triggerCompletion() { completionCb?.invoke() }
}

class AudioEngineTest {

    private val players = mutableListOf<FakePlayer>()

    private fun makeEngine(): AudioEngine {
        players.clear()
        return AudioEngine { FakePlayer().also { players += it } }
    }

    private val lastPlayer get() = players.last()

    // mapVolume tests
    @Test fun `mapVolume 0 returns 0`() = assertEquals(0.0f, mapVolume(0), 0.001f)
    @Test fun `mapVolume 100 returns 1`() = assertEquals(1.0f, mapVolume(100), 0.001f)
    @Test fun `mapVolume 50 returns correct gain`() {
        val expected = (0.5).pow(1.5).toFloat()
        assertEquals(expected, mapVolume(50), 0.001f)
    }
    @Test fun `mapVolume 200 clamps to 1`() = assertEquals(1.0f, mapVolume(200), 0.001f)
    @Test fun `mapVolume 400 clamps to 1`() = assertEquals(1.0f, mapVolume(400), 0.001f)

    // AudioEngine tests
    @Test fun `play calls onStarted`() {
        val engine = makeEngine()
        var started = false
        engine.play("path.mp3", "Sound", 100, false, "h1", onStarted = { started = true }, onDone = {})
        assertTrue(started)
    }

    @Test fun `play prepares and starts the player`() {
        val engine = makeEngine()
        engine.play("audio.mp3", "Test", 100, false, "h1", onStarted = {}, onDone = {})
        assertTrue(lastPlayer.prepareCalled)
        assertTrue(lastPlayer.startCalled)
    }

    @Test fun `activeHandles contains handle after play`() {
        val engine = makeEngine()
        engine.play("p.mp3", "S", 100, false, "h1", onStarted = {}, onDone = {})
        assertIn("h1", engine.activeHandles())
    }

    @Test fun `stop removes handle and fires onDone with stopped`() {
        val engine = makeEngine()
        var reason = ""
        engine.play("p.mp3", "S", 100, false, "h1", onStarted = {}, onDone = { reason = it })
        engine.stop("h1")
        assertFalse("h1" in engine.activeHandles())
        assertEquals("stopped", reason)
    }

    @Test fun `stop calls player stop and release`() {
        val engine = makeEngine()
        engine.play("p.mp3", "S", 100, false, "h1", onStarted = {}, onDone = {})
        val player = lastPlayer
        engine.stop("h1")
        assertTrue(player.stopCalled)
        assertTrue(player.releaseCalled)
    }

    @Test fun `completion listener fires onDone with completed`() {
        val engine = makeEngine()
        var reason = ""
        engine.play("p.mp3", "S", 100, false, "h1", onStarted = {}, onDone = { reason = it })
        lastPlayer.triggerCompletion()
        assertEquals("completed", reason)
        assertFalse("h1" in engine.activeHandles())
    }

    @Test fun `stopAll clears all handles and fires onDone for each`() {
        val engine = makeEngine()
        val reasons = mutableListOf<String>()
        engine.play("p.mp3", "A", 100, false, "h1", onStarted = {}, onDone = { reasons += it })
        engine.play("q.mp3", "B", 100, false, "h2", onStarted = {}, onDone = { reasons += it })
        engine.stopAll()
        assertTrue(engine.activeHandles().isEmpty())
        assertEquals(listOf("stopped", "stopped"), reasons.sorted())
    }

    @Test fun `fireConnectionLost fires onDone with connection_lost for all`() {
        val engine = makeEngine()
        val reasons = mutableListOf<String>()
        engine.play("p.mp3", "S", 100, false, "h1", onStarted = {}, onDone = { reasons += it })
        engine.fireConnectionLost()
        assertEquals(listOf("connection_lost"), reasons)
        assertTrue(engine.activeHandles().isEmpty())
    }

    @Test fun `two simultaneous handles both tracked`() {
        val engine = makeEngine()
        engine.play("a.mp3", "A", 100, false, "h1", onStarted = {}, onDone = {})
        engine.play("b.mp3", "B", 100, false, "h2", onStarted = {}, onDone = {})
        assertIn("h1", engine.activeHandles())
        assertIn("h2", engine.activeHandles())
    }

    @Test fun `play returns false when player prepare throws`() {
        val engine = AudioEngine {
            object : Player {
                override fun prepare(filePath: String, volume: Float, loop: Boolean) {
                    throw IllegalStateException("No media")
                }
                override fun start() {}
                override fun stop() {}
                override fun release() {}
                override fun setOnCompletionListener(cb: () -> Unit) {}
                override fun setOnErrorListener(cb: (Int, Int) -> Boolean) {}
            }
        }
        val result = engine.play("bad.mp3", "Bad", 100, false, "h1", onStarted = {}, onDone = {})
        assertFalse(result)
        assertFalse("h1" in engine.activeHandles())
    }

    @Test fun `loop stop plays to loop end not sample end — stop fires STOPPED`() {
        val engine = makeEngine()
        var reason = ""
        engine.play("loop.mp3", "L", 100, true, "h1", onStarted = {}, onDone = { reason = it })
        engine.stop("h1")
        assertEquals("stopped", reason)
    }
}

private fun assertIn(item: String, list: List<String>) = assertTrue("$item not in $list", item in list)
