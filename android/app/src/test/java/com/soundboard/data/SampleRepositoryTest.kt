package com.soundboard.data

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File

class SampleRepositoryTest {

    private val dao: SampleDao = mockk()
    private val fileStore: FileStore = mockk()
    private lateinit var repo: SampleRepository

    private val sampleEntity = SampleEntity(
        id = "test-id",
        name = "Test Sample",
        filePath = "/sounds/test-id.wav",
        durationMs = 5000L,
        defaultVolume = 100,
        loop = false,
        loopStartMs = 0L,
        loopEndMs = 0L,
        createdAt = 1000L,
    )

    @Before
    fun setup() {
        repo = SampleRepository(dao, fileStore)
    }

    @Test
    fun `import calls fileStore importFile and dao insert, returns entity with correct fields`() = runTest {
        val tmpDir = createTempDir()
        val sourceFile = File(tmpDir, "clip.wav").also { it.writeText("data") }
        val fakeDestPath = "/sounds/some-uuid.wav"

        every { fileStore.importFile(any(), any()) } returns fakeDestPath
        coEvery { dao.insert(any()) } just runs

        val result = repo.import(
            name = "Test",
            sourceFile = sourceFile,
            durationMs = 2000L,
            defaultVolume = 80,
            loop = true,
            loopStartMs = 500L,
            loopEndMs = 1500L,
        )

        verify { fileStore.importFile(any(), sourceFile) }
        coVerify { dao.insert(result) }
        assertEquals("Test", result.name)
        assertEquals(fakeDestPath, result.filePath)
        assertEquals(2000L, result.durationMs)
        assertEquals(80, result.defaultVolume)
        assertEquals(true, result.loop)
        assertEquals(500L, result.loopStartMs)
        assertEquals(1500L, result.loopEndMs)
        assertNotNull(result.id)

        tmpDir.deleteRecursively()
    }

    @Test
    fun `import uses default values when not supplied`() = runTest {
        val tmpDir = createTempDir()
        val sourceFile = File(tmpDir, "clip.mp3").also { it.writeText("audio") }

        every { fileStore.importFile(any(), any()) } returns "/sounds/x.mp3"
        coEvery { dao.insert(any()) } just runs

        val result = repo.import(name = "Default", sourceFile = sourceFile, durationMs = 1000L)

        assertEquals(100, result.defaultVolume)
        assertEquals(false, result.loop)
        assertEquals(0L, result.loopStartMs)
        assertEquals(0L, result.loopEndMs)

        tmpDir.deleteRecursively()
    }

    @Test
    fun `delete calls dao delete and fileStore deleteFile`() = runTest {
        coEvery { dao.delete(any()) } just runs
        every { fileStore.deleteFile(any()) } returns true

        repo.delete(sampleEntity)

        coVerify { dao.delete(sampleEntity) }
        verify { fileStore.deleteFile(sampleEntity.filePath) }
    }

    @Test
    fun `getById delegates to dao getById`() = runTest {
        coEvery { dao.getById("test-id") } returns sampleEntity

        val result = repo.getById("test-id")

        assertEquals(sampleEntity, result)
        coVerify { dao.getById("test-id") }
    }

    @Test
    fun `getById returns null when dao returns null`() = runTest {
        coEvery { dao.getById("missing") } returns null

        val result = repo.getById("missing")

        assertNull(result)
    }

    @Test
    fun `getAll delegates to dao getAll`() = runTest {
        val list = listOf(sampleEntity)
        coEvery { dao.getAll() } returns list

        val result = repo.getAll()

        assertEquals(list, result)
        coVerify { dao.getAll() }
    }

    @Test
    fun `update calls dao insert with the given entity`() = runTest {
        coEvery { dao.insert(any()) } just runs

        repo.update(sampleEntity)

        coVerify { dao.insert(sampleEntity) }
    }

    @Test
    fun `observeAll delegates to dao observeAll`() {
        val flow = flowOf(listOf(sampleEntity))
        every { dao.observeAll() } returns flow

        val result = repo.observeAll()

        assertEquals(flow, result)
        verify { dao.observeAll() }
    }
}
