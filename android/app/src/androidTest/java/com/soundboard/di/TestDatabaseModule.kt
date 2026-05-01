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
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.io.File
import javax.inject.Singleton

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DatabaseModule::class])
object TestDatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): SoundboardDatabase =
        Room.inMemoryDatabaseBuilder(ctx, SoundboardDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    @Provides
    fun provideSampleDao(db: SoundboardDatabase): SampleDao = db.sampleDao()

    @Provides
    @Singleton
    fun provideFileStore(@ApplicationContext ctx: Context): FileStore =
        FileStore(File(ctx.cacheDir, "test-sounds"))

    @Provides
    @Singleton
    fun provideSampleRepository(dao: SampleDao, fileStore: FileStore): SampleRepository =
        SampleRepository(dao, fileStore)

    @Provides
    @Singleton
    fun providePlaybackRepository(): PlaybackRepository = PlaybackRepository()
}
