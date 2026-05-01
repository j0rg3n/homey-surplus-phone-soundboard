package com.soundboard.service

import com.soundboard.DoneReason
import kotlin.math.pow

fun mapVolume(volume: Int): Float =
    (volume / 100.0).pow(1.5).toFloat().coerceAtMost(1.0f)

class AudioEngine {
    private data class PlaybackEntry(
        val soundId: String,
        val onDone: (String) -> Unit
    )
    private val active = mutableMapOf<String, PlaybackEntry>()

    fun play(
        soundId: String,
        volume: Int,
        handle: String,
        onStarted: () -> Unit,
        onDone: (reason: String) -> Unit
    ): Boolean {
        active[handle] = PlaybackEntry(soundId, onDone)
        onStarted()
        return true
    }

    fun stop(handle: String) {
        active.remove(handle)?.let { it.onDone(DoneReason.STOPPED) }
    }

    fun stopAll() {
        val entries = active.values.toList()
        active.clear()
        entries.forEach { it.onDone(DoneReason.STOPPED) }
    }

    fun fireConnectionLost() {
        val entries = active.values.toList()
        active.clear()
        entries.forEach { it.onDone(DoneReason.CONNECTION_LOST) }
    }

    fun activeHandles(): List<String> = active.keys.toList()
}
