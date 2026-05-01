package com.soundboard.di

import android.content.Context
import androidx.room.Room
import com.soundboard.data.FileStore
import com.soundboard.data.PlaybackRepository
import com.soundboard.data.SampleDao
import com.soundboard.data.SampleRepository
import com.soundboard.data.SoundboardDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): SoundboardDatabase =
        Room.databaseBuilder(ctx, SoundboardDatabase::class.java, "soundboard.db").build()

    @Provides
    fun provideSampleDao(db: SoundboardDatabase): SampleDao = db.sampleDao()

    @Provides
    @Singleton
    fun provideFileStore(@ApplicationContext ctx: Context): FileStore =
        FileStore(File(ctx.filesDir, "sounds"))

    @Provides
    @Singleton
    fun provideSampleRepository(dao: SampleDao, fileStore: FileStore): SampleRepository =
        SampleRepository(dao, fileStore)

    @Provides
    @Singleton
    fun providePlaybackRepository(): PlaybackRepository = PlaybackRepository()
}
