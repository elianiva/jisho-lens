package com.elianiva.jisholens.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elianiva.jisholens.data.DictionaryRepository
import com.elianiva.jisholens.data.NotesRepository
import com.elianiva.jisholens.data.SearchQuery
import com.elianiva.jisholens.data.SettingsRepository
import com.elianiva.jisholens.domain.Entry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
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
    private val notes: NotesRepository,
    private val savedStateHandle: SavedStateHandle,
    initialQuery: String? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(
        SearchUiState(
            query = initialQuery ?: savedStateHandle.get<String>(QUERY_KEY).orEmpty(),
        ),
    )
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    val recents: StateFlow<List<String>> = settings.recentSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Saved notes, keyed by headword, so every entry card can show its own. */
    val savedNotes: StateFlow<Map<String, String>> = notes.notes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private var debounceJob: Job? = null
    private var searchJob: Job? = null
    private var recentJob: Job? = null

    init {
        if (_state.value.query.isNotBlank()) search()
    }

    /** Searches as you type, once the input has been still for [DEBOUNCE_MILLIS]. */
    fun onQueryChange(query: String) {
        savedStateHandle[QUERY_KEY] = query
        _state.update { it.copy(query = query) }
        debounceJob?.cancel()
        if (query.isBlank()) {
            searchJob?.cancel()
            recentJob?.cancel()
            _state.value = SearchUiState(query = query)
            return
        }
        debounceJob = viewModelScope.launch {
            delay(DEBOUNCE_MILLIS)
            search()
        }
    }

    fun submit() {
        debounceJob?.cancel()
        search()
    }

    fun searchLink(text: String) {
        debounceJob?.cancel()
        savedStateHandle[QUERY_KEY] = text
        _state.update { it.copy(query = text) }
        search()
    }

    fun clear() {
        debounceJob?.cancel()
        searchJob?.cancel()
        recentJob?.cancel()
        savedStateHandle[QUERY_KEY] = ""
        _state.value = SearchUiState()
    }

    fun clearRecents() {
        viewModelScope.launch { settings.clearRecentSearches() }
    }

    fun saveNote(expression: String, note: String) {
        viewModelScope.launch { notes.setNote(expression, note) }
    }

    private fun search() {
        val query = _state.value.query.trim()
        if (query.isEmpty()) return
        searchJob?.cancel()
        recentJob?.cancel()
        searchJob = viewModelScope.launch {
            // Keep the previous results on screen while the next query runs, so typing does not flicker.
            _state.update { it.copy(isLoading = true, hasSearched = true, error = null) }
            try {
                val current = settings.settings.first()
                val entries = if (SearchQuery.useFullText(query, current.fullTextSearch)) {
                    repository.search(query)
                } else {
                    repository.lookup(query)
                }
                _state.update { it.copy(isLoading = false, results = entries) }
                if (entries.isNotEmpty()) rememberLater(query)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update {
                    it.copy(isLoading = false, results = emptyList(), error = error.message ?: "The search failed")
                }
            }
        }
    }

    /** Saves a lookup to recents only if the person stays on it, so half-typed words are skipped. */
    private fun rememberLater(query: String) {
        recentJob = viewModelScope.launch {
            delay(RECENT_DELAY_MILLIS)
            settings.addRecentSearch(query)
        }
    }

    private companion object {
        const val QUERY_KEY = "query"
        const val DEBOUNCE_MILLIS = 250L
        const val RECENT_DELAY_MILLIS = 1_500L
    }
}
