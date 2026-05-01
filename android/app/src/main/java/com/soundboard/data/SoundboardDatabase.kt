package com.soundboard.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [SampleEntity::class], version = 1)
abstract class SoundboardDatabase : RoomDatabase() {
    abstract fun sampleDao(): SampleDao
}
