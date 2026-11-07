package com.elianiva.jisholens.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elianiva.jisholens.data.DictionaryRepository
import com.elianiva.jisholens.data.SearchQuery
import com.elianiva.jisholens.data.SettingsRepository
import com.elianiva.jisholens.domain.Entry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: List<Entry> = emptyList(),
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false,
    val error: String? = null,
)

class SearchViewModel(
    private val repository: DictionaryRepository,
    private val settings: SettingsRepository,
    private val savedStateHandle: SavedStateHandle,
    initialQuery: String? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(
        SearchUiState(
            query = initialQuery ?: savedStateHandle.get<String>(QUERY_KEY).orEmpty(),
        ),
    )
    val state: StateFlow<SearchUiState> = _state.asStateFlow()
    private var searchJob: Job? = null

    init {
        if (_state.value.query.isNotBlank()) search()
    }

    fun onQueryChange(query: String) {
        savedStateHandle[QUERY_KEY] = query
        _state.update { it.copy(query = query) }
    }

    fun submit() {
        search()
    }

    fun searchLink(text: String) {
        onQueryChange(text)
        search()
    }

    fun clear() {
        searchJob?.cancel()
        savedStateHandle[QUERY_KEY] = ""
        _state.value = SearchUiState()
    }

    private fun search() {
        val query = _state.value.query.trim()
        if (query.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, hasSearched = true, error = null, results = emptyList()) }
            try {
                val current = settings.settings.first()
                val entries = if (SearchQuery.useFullText(query, current.fullTextSearch)) {
                    repository.search(query)
                } else {
                    repository.lookup(query)
                }
                _state.update { it.copy(isLoading = false, results = entries) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update {
                    it.copy(isLoading = false, error = error.message ?: "The search failed")
                }
            }
        }
    }

    private companion object {
        const val QUERY_KEY = "query"
    }
}
