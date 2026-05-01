package com.soundboard.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "samples")
data class SampleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val filePath: String,
    val durationMs: Long,
    val defaultVolume: Int,
    val loop: Boolean,
    val loopStartMs: Long,
    val loopEndMs: Long,
    val createdAt: Long,
)
