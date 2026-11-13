package com.elianiva.jisholens.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elianiva.jisholens.data.DictionaryRepository
import com.elianiva.jisholens.data.NotesRepository
import com.elianiva.jisholens.data.SettingsRepository
import com.elianiva.jisholens.domain.Entry
import com.elianiva.jisholens.domain.ScanWord
import com.elianiva.jisholens.domain.decodeReaderText
import com.elianiva.jisholens.domain.headword
import com.elianiva.jisholens.domain.readerFormat
import com.elianiva.jisholens.domain.readerLines
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReaderUiState(
    val fileName: String? = null,
    val lines: List<String> = emptyList(),
    val selectedLine: Int = -1,
    val candidates: List<ScanWord> = emptyList(),
    val selectedCandidate: Int = 0,
    val expanded: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
) {
    val line: String? get() = lines.getOrNull(selectedLine)
    val word: ScanWord? get() = candidates.getOrNull(selectedCandidate)
    val entries: List<Entry> get() = word?.entries.orEmpty()
}

/**
 * Reads one imported text or subtitle file and answers taps on its lines.
 *
 * The file is read once into memory; the reader never writes it back and never
 * sends it anywhere. A tap goes through the same lookup and candidate ranking
 * as the lens, so the sheet behaves the same in both places.
 */
class ReaderViewModel(
    context: Context,
    private val repository: DictionaryRepository,
    private val settings: SettingsRepository,
    private val notes: NotesRepository,
) : ViewModel() {
    private val context = context.applicationContext
    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()
    val knownWords: StateFlow<Set<String>> = settings.knownWords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    val savedNotes: StateFlow<Map<String, String>> = notes.notes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    private var selectionJob: Job? = null

    fun import(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val name = displayName(uri) ?: "Untitled"
                val format = readerFormat(name)
                    ?: error("Choose a .txt, .srt, or .vtt file.")
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("The file could not be opened.")
                }
                val lines = readerLines(decodeReaderText(bytes), format)
                if (lines.isEmpty()) error("The file has no lines to read.")
                _state.value = ReaderUiState(fileName = name, lines = lines)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.value = ReaderUiState(
                    loading = false,
                    error = error.message ?: "The file could not be read.",
                )
            }
        }
    }

    fun tap(lineIndex: Int, offset: Int) {
        val line = _state.value.lines.getOrNull(lineIndex) ?: return
        selectionJob?.cancel()
        _state.update {
            it.copy(
                selectedLine = lineIndex,
                candidates = emptyList(),
                selectedCandidate = 0,
                expanded = false,
            )
        }
        selectionJob = viewModelScope.launch {
            val words = try {
                repository.scanAt(line, offset)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
            _state.update {
                if (it.selectedLine == lineIndex) it.copy(candidates = words) else it
            }
        }
    }

    fun selectCandidate(index: Int) {
        if (index !in _state.value.candidates.indices) return
        _state.update { it.copy(selectedCandidate = index) }
    }

    fun clearSelection() {
        selectionJob?.cancel()
        _state.update {
            it.copy(selectedLine = -1, candidates = emptyList(), selectedCandidate = 0, expanded = false)
        }
    }

    fun expand() {
        if (_state.value.entries.isEmpty()) return
        _state.update { it.copy(expanded = true) }
    }

    fun collapse() {
        _state.update { it.copy(expanded = false) }
    }

    fun toggleKnown() {
        val expression = _state.value.word?.headword() ?: return
        val known = expression in knownWords.value
        viewModelScope.launch { settings.setKnown(expression, !known) }
    }

    fun saveNote(expression: String, note: String) {
        viewModelScope.launch { notes.setNote(expression, note) }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }.getOrNull()
}
