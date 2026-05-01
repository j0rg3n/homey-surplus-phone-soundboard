package com.soundboard.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class FileStoreTest {

    private lateinit var tmpDir: File
    private lateinit var soundsDir: File
    private lateinit var store: FileStore

    @Before
    fun setup() {
        tmpDir = createTempDir()
        soundsDir = File(tmpDir, "sounds")
        store = FileStore(soundsDir)
    }

    @After
    fun cleanup() {
        tmpDir.deleteRecursively()
    }

    @Test
    fun `soundsDir is created on init`() {
        assertTrue("soundsDir should exist after init", soundsDir.exists())
        assertTrue("soundsDir should be a directory", soundsDir.isDirectory)
    }

    @Test
    fun `importFile copies file contents correctly`() {
        val sourceFile = File(tmpDir, "test.wav")
        sourceFile.writeBytes(byteArrayOf(1, 2, 3, 4, 5))

        val destPath = store.importFile("abc-123", sourceFile)

        val destFile = File(destPath)
        assertTrue("Destination file should exist", destFile.exists())
        assertTrue("Destination file contents should match source",
            sourceFile.readBytes().contentEquals(destFile.readBytes()))
    }

    @Test
    fun `importFile returns path inside soundsDir`() {
        val sourceFile = File(tmpDir, "clip.mp3")
        sourceFile.writeText("audio data")

        val destPath = store.importFile("my-id", sourceFile)

        assertTrue("Returned path should be inside soundsDir",
            destPath.startsWith(soundsDir.absolutePath))
        assertTrue("Returned path should contain the id", destPath.contains("my-id"))
        assertTrue("Returned path should keep the extension", destPath.endsWith(".mp3"))
    }

    @Test
    fun `importFile overwrites existing file with same id`() {
        val sourceFile = File(tmpDir, "sound.wav")
        sourceFile.writeBytes(byteArrayOf(10, 20, 30))
        store.importFile("same-id", sourceFile)

        val updatedSource = File(tmpDir, "sound2.wav")
        updatedSource.writeBytes(byteArrayOf(99))
        // Copy with same id and same extension
        val sourceFileNew = File(tmpDir, "sound.wav")
        sourceFileNew.writeBytes(byteArrayOf(99))
        val destPath = store.importFile("same-id", sourceFileNew)

        assertEquals(byteArrayOf(99).toList(), File(destPath).readBytes().toList())
    }

    @Test
    fun `deleteFile removes the file and returns true`() {
        val sourceFile = File(tmpDir, "delete_me.wav")
        sourceFile.writeText("data")
        val destPath = store.importFile("del-id", sourceFile)

        val result = store.deleteFile(destPath)

        assertTrue("deleteFile should return true when file existed", result)
        assertFalse("File should no longer exist after deletion", File(destPath).exists())
    }

    @Test
    fun `deleteFile on nonexistent file returns false`() {
        val nonExistentPath = File(soundsDir, "ghost.wav").absolutePath
        val result = store.deleteFile(nonExistentPath)
        assertFalse("deleteFile should return false for nonexistent file", result)
    }

    @Test
    fun `pathFor returns expected path string`() {
        val expected = File(soundsDir, "uuid-xyz.ogg").absolutePath
        val actual = store.pathFor("uuid-xyz", "ogg")
        assertEquals(expected, actual)
    }

    @Test
    fun `pathFor does not create the file`() {
        val path = store.pathFor("no-create", "wav")
        assertFalse("pathFor should not create the file", File(path).exists())
    }

    @Test
    fun `importStream writes stream contents to soundsDir with correct extension`() {
        val data = byteArrayOf(10, 20, 30, 40)
        val destPath = store.importStream("stream-id", data.inputStream(), "wav")

        val destFile = File(destPath)
        assertTrue("Destination file should exist", destFile.exists())
        assertEquals(data.toList(), destFile.readBytes().toList())
        assertTrue("Path should be inside soundsDir", destPath.startsWith(soundsDir.absolutePath))
        assertTrue("Path should contain the id", destPath.contains("stream-id"))
        assertTrue("Path should end with .wav", destPath.endsWith(".wav"))
    }

    @Test
    fun `importStream overwrites existing file with same id`() {
        store.importStream("same-id", byteArrayOf(1, 2, 3).inputStream(), "mp3")
        val destPath = store.importStream("same-id", byteArrayOf(99).inputStream(), "mp3")
        assertEquals(listOf<Byte>(99), File(destPath).readBytes().toList())
    }
}
