package com.soundboard.data

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

    suspend fun delete(sample: SampleEntity) {
        dao.delete(sample)
        fileStore.deleteFile(sample.filePath)
    }

    suspend fun update(sample: SampleEntity) = dao.insert(sample)
}
