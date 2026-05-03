package com.soundboard.ui

import androidx.lifecycle.ViewModel
import com.soundboard.data.ActivePlayback
import com.soundboard.data.PlaybackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val playbackRepository: PlaybackRepository,
) : ViewModel() {
    val active: StateFlow<Map<String, ActivePlayback>> = playbackRepository.active
    val isMuted: StateFlow<Boolean> = playbackRepository.isMuted

    fun stop(handle: String) {
        playbackRepository.requestStop(handle)
    }
}
