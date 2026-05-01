package com.soundboard

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SoundboardApp : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}