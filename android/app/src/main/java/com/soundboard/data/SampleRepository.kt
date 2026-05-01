package com.soundboard.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.flow.Flow
import java.io.File

class SampleRepository(
    private val dao: SampleDao,
    private val fileStore: FileStore,
) {
    fun observeAll(): Flow<List<SampleEntity>> = dao.observeAll()

    suspend fun getAll(): List<SampleEntity> = dao.getAll()

    suspend fun getById(id: String): SampleEntity? = dao.getById(id)

    suspend fun import(
        name: String,
        sourceFile: File,
        durationMs: Long,
        defaultVolume: Int = 100,
        loop: Boolean = false,
        loopStartMs: Long = 0,
        loopEndMs: Long = 0,
    ): SampleEntity {
        val id = java.util.UUID.randomUUID().toString()
        val filePath = fileStore.importFile(id, sourceFile)
        val entity = SampleEntity(
            id = id,
            name = name,
            filePath = filePath,
            durationMs = durationMs,
            defaultVolume = defaultVolume,
            loop = loop,
            loopStartMs = loopStartMs,
            loopEndMs = loopEndMs,
            createdAt = System.currentTimeMillis(),
        )
        dao.insert(entity)
        return entity
    }

    suspend fun importFromUri(context: Context, uri: Uri): SampleEntity {
        val cr = context.contentResolver

        val displayName = cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            ?: uri.lastPathSegment ?: "Unknown"
        val name = displayName.substringBeforeLast('.')
        val ext = displayName.substringAfterLast('.', "mp3")

        val durationMs = try {
            MediaMetadataRetriever().use { mmr ->
                mmr.setDataSource(context, uri)
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
            }
        } catch (_: Exception) { 0L }

        val id = java.util.UUID.randomUUID().toString()
        val filePath = cr.openInputStream(uri)!!.use { stream ->
            fileStore.importStream(id, stream, ext)
        }
        val entity = SampleEntity(
            id = id, name = name, filePath = filePath,
            durationMs = durationMs, defaultVolume = 100,
            loop = false, loopStartMs = 0, loopEndMs = durationMs,
            createdAt = System.currentTimeMillis(),
        )
        dao.insert(entity)
        return entity
    }

    suspend fun delete(sample: SampleEntity) {
        dao.delete(sample)
        fileStore.deleteFile(sample.filePath)
    }

    suspend fun update(sample: SampleEntity) = dao.insert(sample)
}
