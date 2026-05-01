package com.soundboard.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ActivePlayback(
    val handle: String,
    val sampleId: String,
    val sampleName: String,
    val startedAt: Long,
    val durationMs: Long, // 0 for looping
)

class PlaybackRepository {
    private val _active = MutableStateFlow<Map<String, ActivePlayback>>(emptyMap())
    val active: StateFlow<Map<String, ActivePlayback>> = _active.asStateFlow()

    fun add(playback: ActivePlayback) {
        _active.update { it + (playback.handle to playback) }
    }

    fun remove(handle: String) {
        _active.update { it - handle }
    }

    fun clear() {
        _active.value = emptyMap()
    }

    fun getAll(): List<ActivePlayback> = _active.value.values.toList()
}
