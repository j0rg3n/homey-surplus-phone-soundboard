package com.soundboard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soundboard.data.SampleEntity
import com.soundboard.data.SampleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val sampleRepository: SampleRepository,
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

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    suspend fun deleteSample(sample: SampleEntity) {
        sampleRepository.delete(sample)
    }

    suspend fun updateSample(sample: SampleEntity) {
        sampleRepository.update(sample)
    }
}
