package com.soundboard.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SampleDao {
    @Query("SELECT * FROM samples ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<SampleEntity>>

    @Query("SELECT * FROM samples WHERE id = :id")
    suspend fun getById(id: String): SampleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sample: SampleEntity)

    @Delete
    suspend fun delete(sample: SampleEntity)

    @Query("SELECT * FROM samples")
    suspend fun getAll(): List<SampleEntity>
}
