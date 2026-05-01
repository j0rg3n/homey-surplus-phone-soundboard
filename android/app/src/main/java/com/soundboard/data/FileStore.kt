package com.soundboard.data

import java.io.File

class FileStore(private val soundsDir: File) {
    init {
        soundsDir.mkdirs()
    }

    /**
     * Copies [sourceFile] into soundsDir/<id>.<ext> and returns the destination absolute path.
     */
    fun importFile(id: String, sourceFile: File): String {
        val ext = sourceFile.extension
        val dest = File(soundsDir, "$id.$ext")
        sourceFile.copyTo(dest, overwrite = true)
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
