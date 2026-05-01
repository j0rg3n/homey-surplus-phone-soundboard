package com.soundboard.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlaybackRepositoryTest {

    private lateinit var repo: PlaybackRepository

    private fun makePlayback(handle: String, sampleId: String = "s1") = ActivePlayback(
        handle = handle,
        sampleId = sampleId,
        sampleName = "Sample $sampleId",
        startedAt = 1000L,
        durationMs = 3000L,
    )

    @Before
    fun setup() {
        repo = PlaybackRepository()
    }

    @Test
    fun `initial active map is empty`() {
        assertTrue(repo.active.value.isEmpty())
    }

    @Test
    fun `getAll returns empty list initially`() {
        assertTrue(repo.getAll().isEmpty())
    }

    @Test
    fun `add emits updated map containing the handle`() = runTest {
        val pb = makePlayback("h1")
        repo.add(pb)

        val map = repo.active.first()
        assertTrue("Map should contain handle h1", map.containsKey("h1"))
        assertEquals(pb, map["h1"])
    }

    @Test
    fun `remove emits map without the handle`() = runTest {
        repo.add(makePlayback("h1"))
        repo.add(makePlayback("h2"))

        repo.remove("h1")

        val map = repo.active.first()
        assertFalse("h1 should have been removed", map.containsKey("h1"))
        assertTrue("h2 should still be present", map.containsKey("h2"))
    }

    @Test
    fun `remove of unknown handle leaves map unchanged`() = runTest {
        repo.add(makePlayback("h1"))
        repo.remove("nonexistent")

        val map = repo.active.first()
        assertEquals(1, map.size)
        assertTrue(map.containsKey("h1"))
    }

    @Test
    fun `clear emits empty map`() = runTest {
        repo.add(makePlayback("h1"))
        repo.add(makePlayback("h2"))

        repo.clear()

        val map = repo.active.first()
        assertTrue("Map should be empty after clear", map.isEmpty())
    }

    @Test
    fun `two simultaneous adds both appear in StateFlow`() = runTest {
        val pb1 = makePlayback("h1", "s1")
        val pb2 = makePlayback("h2", "s2")
        repo.add(pb1)
        repo.add(pb2)

        val map = repo.active.first()
        assertEquals(2, map.size)
        assertEquals(pb1, map["h1"])
        assertEquals(pb2, map["h2"])
    }

    @Test
    fun `StateFlow can be collected with first()`() = runTest {
        val pb = makePlayback("h3")
        repo.add(pb)

        val map = repo.active.first()
        assertEquals(pb, map["h3"])
    }

    @Test
    fun `getAll returns list of all active playbacks`() {
        val pb1 = makePlayback("h1")
        val pb2 = makePlayback("h2")
        repo.add(pb1)
        repo.add(pb2)

        val all = repo.getAll()
        assertEquals(2, all.size)
        assertTrue(all.contains(pb1))
        assertTrue(all.contains(pb2))
    }

    @Test
    fun `getAll returns empty list after clear`() {
        repo.add(makePlayback("h1"))
        repo.clear()
        assertTrue(repo.getAll().isEmpty())
    }

    @Test
    fun `add with looping durationMs zero is stored correctly`() = runTest {
        val looping = ActivePlayback(
            handle = "loop-h",
            sampleId = "s-loop",
            sampleName = "Loop Sample",
            startedAt = 5000L,
            durationMs = 0L,
        )
        repo.add(looping)

        val map = repo.active.first()
        assertEquals(0L, map["loop-h"]?.durationMs)
    }
}
