package com.soundboard.service

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.pow

class AudioEngineTest {

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
        val engine = AudioEngine()
        var started = false
        engine.play("snd", 100, "h1", onStarted = { started = true }, onDone = {})
        assertTrue(started)
    }
    @Test fun `activeHandles contains handle after play`() {
        val engine = AudioEngine()
        engine.play("snd", 100, "h1", onStarted = {}, onDone = {})
        assertIn("h1", engine.activeHandles())
    }
    @Test fun `stop removes handle and fires onDone with stopped`() {
        val engine = AudioEngine()
        var reason = ""
        engine.play("snd", 100, "h1", onStarted = {}, onDone = { reason = it })
        engine.stop("h1")
        assertFalse("h1" in engine.activeHandles())
        assertEquals("stopped", reason)
    }
    @Test fun `stopAll clears all handles and fires onDone for each`() {
        val engine = AudioEngine()
        val reasons = mutableListOf<String>()
        engine.play("snd", 100, "h1", onStarted = {}, onDone = { reasons += it })
        engine.play("snd", 100, "h2", onStarted = {}, onDone = { reasons += it })
        engine.stopAll()
        assertTrue(engine.activeHandles().isEmpty())
        assertEquals(listOf("stopped", "stopped"), reasons.sorted())
    }
    @Test fun `fireConnectionLost fires onDone with connection_lost for all`() {
        val engine = AudioEngine()
        val reasons = mutableListOf<String>()
        engine.play("snd", 100, "h1", onStarted = {}, onDone = { reasons += it })
        engine.fireConnectionLost()
        assertEquals(listOf("connection_lost"), reasons)
    }
    @Test fun `two simultaneous handles both tracked`() {
        val engine = AudioEngine()
        engine.play("a", 100, "h1", onStarted = {}, onDone = {})
        engine.play("b", 100, "h2", onStarted = {}, onDone = {})
        assertTrue("h1" in engine.activeHandles())
        assertTrue("h2" in engine.activeHandles())
    }
}

private fun assertIn(item: String, list: List<String>) = assertTrue("$item not in $list", item in list)
