package com.soundboard.data

import java.io.File
import java.io.InputStream

class FileStore(private val soundsDir: File) {
    init {
        soundsDir.mkdirs()
    }

    fun importFile(id: String, sourceFile: File): String {
        val ext = sourceFile.extension
        val dest = File(soundsDir, "$id.$ext")
        sourceFile.copyTo(dest, overwrite = true)
        return dest.absolutePath
    }

    fun importStream(id: String, inputStream: InputStream, ext: String): String {
        val dest = File(soundsDir, "$id.$ext")
        inputStream.use { it.copyTo(dest.outputStream()) }
        return dest.absolutePath
    }

    /**
     * Deletes the file at [filePath]. Returns true if the file was deleted, false otherwise.
     */
    fun deleteFile(filePath: String): Boolean = File(filePath).delete()

    /**
     * Returns the expected absolute path for a given [id] and [ext] (without leading dot).
     */
    fun pathFor(id: String, ext: String): String = File(soundsDir, "$id.$ext").absolutePath
}
