package com.elianiva.jisholens.ui.screens

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elianiva.jisholens.data.DictionaryRepository
import com.elianiva.jisholens.data.ImageLoader
import com.elianiva.jisholens.data.OcrEngine
import com.elianiva.jisholens.data.TextLineBox
import com.elianiva.jisholens.domain.Entry
import com.elianiva.jisholens.domain.ScanWord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

data class ScanUiState(
    val bitmap: Bitmap? = null,
    val lines: List<TextLineBox> = emptyList(),
    val selectedLine: Int = -1,
    val words: List<ScanWord> = emptyList(),
    val selectedWord: Int = -1,
    val entries: List<Entry> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

class ScanViewModel(
    private val context: Context,
    private val repository: DictionaryRepository,
    private val ocr: OcrEngine,
    private val imagePath: String,
) : ViewModel() {
    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()
    private var lineJob: Job? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val bitmap = withContext(Dispatchers.IO) { ImageLoader.load(context, imagePath) }
                val lines = ocr.recognize(bitmap)
                _state.value = ScanUiState(bitmap = bitmap, lines = lines, loading = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update { it.copy(loading = false, error = error.message ?: "The image could not be scanned") }
            }
        }
    }

    fun onTap(imageX: Float, imageY: Float) {
        val lineIndex = lineAt(_state.value.lines, imageX, imageY)
        if (lineIndex < 0) {
            _state.update { it.copy(selectedLine = -1, words = emptyList(), selectedWord = -1, entries = emptyList()) }
            return
        }
        selectLine(lineIndex, imageX)
    }

    fun selectLine(index: Int, imageX: Float? = null) {
        val line = _state.value.lines.getOrNull(index) ?: return
        val charIndex = charIndexAt(line, imageX)
        lineJob?.cancel()
        _state.update {
            it.copy(selectedLine = index, words = emptyList(), selectedWord = -1, entries = emptyList())
        }
        lineJob = viewModelScope.launch {
            val words = try {
                repository.scan(line.text)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
            val selected = words.indexOfFirst { charIndex in it.start until it.end }
                .takeIf { it >= 0 }
                ?: words.indexOfFirst { it.start >= charIndex }.takeIf { it >= 0 }
                ?: words.indices.firstOrNull()
                ?: -1
            _state.update {
                it.copy(
                    words = words,
                    selectedWord = selected,
                    entries = words.getOrNull(selected)?.entries.orEmpty(),
                )
            }
        }
    }

    fun selectWord(index: Int) {
        val word = _state.value.words.getOrNull(index) ?: return
        _state.update { it.copy(selectedWord = index, entries = word.entries) }
    }

    fun searchLink(text: String) {
        viewModelScope.launch {
            val entries = try {
                repository.lookup(text).ifEmpty { repository.search(text) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
            _state.update { it.copy(entries = entries, selectedWord = -1) }
        }
    }
}
