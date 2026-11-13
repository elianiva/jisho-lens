package com.elianiva.jisholens.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elianiva.jisholens.data.DictionaryRepository
import com.elianiva.jisholens.data.NotesRepository
import com.elianiva.jisholens.domain.Entry
import com.elianiva.jisholens.domain.KanjiDetail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class KanjiUiState(
    val character: String,
    val details: List<KanjiDetail> = emptyList(),
    val words: List<Entry> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
) {
    val detail: KanjiDetail? get() = details.firstOrNull()
}

class KanjiViewModel(
    private val repository: DictionaryRepository,
    private val notes: NotesRepository,
    character: String,
) : ViewModel() {
    private val _state = MutableStateFlow(KanjiUiState(character = character))
    val state: StateFlow<KanjiUiState> = _state.asStateFlow()
    val savedNotes: StateFlow<Map<String, String>> = notes.notes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        load(character)
    }

    fun load(character: String) {
        viewModelScope.launch {
            _state.update { it.copy(character = character, loading = true, error = null) }
            try {
                val details = repository.kanji(character)
                val words = repository.kanjiWords(character)
                _state.update { it.copy(details = details, words = words, loading = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update {
                    it.copy(loading = false, details = emptyList(), words = emptyList(), error = error.message ?: "The kanji could not be read")
                }
            }
        }
    }

    fun saveNote(expression: String, note: String) {
        viewModelScope.launch { notes.setNote(expression, note) }
    }
}
