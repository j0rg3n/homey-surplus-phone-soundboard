package com.soundboard.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soundboard.data.PlaybackRepository
import com.soundboard.data.SampleEntity
import com.soundboard.data.SampleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val sampleRepository: SampleRepository,
    private val playbackRepository: PlaybackRepository,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val samples: StateFlow<List<SampleEntity>> = combine(
        sampleRepository.observeAll(),
        _searchQuery,
    ) { all, query ->
        if (query.isBlank()) all
        else all.filter { it.name.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _previewingSampleId = MutableStateFlow<String?>(null)
    val previewingSampleId: StateFlow<String?> = _previewingSampleId.asStateFlow()

    private var previewPlayer: MediaPlayer? = null

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun importUris(context: Context, uris: List<Uri>) {
        viewModelScope.launch {
            uris.forEach { uri ->
                runCatching { sampleRepository.importFromUri(context, uri) }
            }
        }
    }

    fun previewSound(sample: SampleEntity) {
        if (sample.loop) {
            val activeInstances = playbackRepository.active.value.values.filter { it.sampleId == sample.id }
            if (activeInstances.isNotEmpty()) {
                activeInstances.forEach { playbackRepository.requestStop(it.handle) }
                return
            }
        }
        if (_previewingSampleId.value == sample.id) {
            stopPreview()
            return
        }
        stopPreview()
        _previewingSampleId.value = sample.id
        previewPlayer = MediaPlayer().apply {
            try {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(sample.filePath)
                setOnCompletionListener { _previewingSampleId.value = null }
                setOnErrorListener { _, _, _ -> _previewingSampleId.value = null; true }
                prepare()
                start()
            } catch (_: Exception) {
                release()
                previewPlayer = null
                _previewingSampleId.value = null
            }
        }
    }

    fun stopPreview() {
        previewPlayer?.release()
        previewPlayer = null
        _previewingSampleId.value = null
    }

    suspend fun deleteSample(sample: SampleEntity) {
        sampleRepository.delete(sample)
    }

    suspend fun updateSample(sample: SampleEntity) {
        sampleRepository.update(sample)
    }

    override fun onCleared() {
        super.onCleared()
        stopPreview()
    }
}
