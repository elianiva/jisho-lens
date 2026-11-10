package com.elianiva.jisholens.ui.screens

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elianiva.jisholens.data.DictionaryRepository
import com.elianiva.jisholens.data.OcrEngine
import com.elianiva.jisholens.data.TextLineBox
import com.elianiva.jisholens.domain.Entry
import com.elianiva.jisholens.domain.ScanWord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Why the frozen reader has nothing to show. Every one has a way forward. */
enum class ScreenScanMessage {
    /** The system consent dialog was dismissed. */
    ConsentDenied,

    /** The app has no enabled dictionary yet. */
    NoDictionary,

    /** OCR found no Japanese on the frame. */
    NoText,

    /** The frame is one flat color, which is what a protected window captures as. */
    Protected,

    /** No frame arrived in time, which usually means the capture was stopped. */
    NoFrame,
}

/** One recognised line, segmented into words for reader mode. */
data class ReaderLine(
    val text: String,
    val rect: Rect,
    val words: List<ScanWord>,
)

data class ScreenScanUiState(
    val frame: Bitmap? = null,
    val lines: List<TextLineBox> = emptyList(),
    val loading: Boolean = false,
    val message: ScreenScanMessage? = null,
    val selectedLine: Int = -1,
    val candidates: List<ScanWord> = emptyList(),
    val selectedCandidate: Int = 0,
    val reader: Boolean = false,
    val readerLines: List<ReaderLine> = emptyList(),
    val readingPage: Boolean = false,
    val cardAtTop: Boolean = false,
    val expanded: Boolean = false,
) {
    val word: ScanWord? get() = candidates.getOrNull(selectedCandidate)
    val entries: List<Entry> get() = word?.entries.orEmpty()
    val line: TextLineBox? get() = lines.getOrNull(selectedLine)
}

/**
 * The frozen reader: OCR one frame, then answer taps with [DictionaryRepository.scanAt]
 * instead of the greedy [DictionaryRepository.scan] the chip list uses, so every word
 * that starts at the tap is a candidate.
 */
class ScreenScanViewModel(
    private val repository: DictionaryRepository,
    private val ocr: OcrEngine,
    private val frame: Bitmap?,
    initialMessage: ScreenScanMessage?,
) : ViewModel() {
    private val _state = MutableStateFlow(
        ScreenScanUiState(
            frame = frame,
            loading = frame != null && initialMessage == null,
            message = initialMessage,
        ),
    )
    val state: StateFlow<ScreenScanUiState> = _state.asStateFlow()

    private var selectionJob: Job? = null
    private var readerJob: Job? = null

    init {
        if (frame != null && initialMessage == null) read()
    }

    private fun read() {
        val bitmap = _state.value.frame ?: return
        if (bitmap.isProtected()) {
            _state.update { it.copy(loading = false, message = ScreenScanMessage.Protected) }
            return
        }
        viewModelScope.launch {
            val lines = try {
                withContext(Dispatchers.Default) { ocr.recognize(bitmap) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
            _state.update {
                it.copy(
                    lines = lines,
                    loading = false,
                    message = if (lines.isEmpty()) ScreenScanMessage.NoText else null,
                )
            }
        }
    }

    fun onTap(imageX: Float, imageY: Float) {
        val index = lineAt(_state.value.lines, imageX, imageY)
        if (index < 0) {
            clearSelection()
            return
        }
        selectLine(index, imageX)
    }

    fun selectLine(index: Int, imageX: Float? = null) {
        val current = _state.value
        val line = current.lines.getOrNull(index) ?: return
        val offset = charIndexAt(line, imageX)
        selectionJob?.cancel()
        _state.update {
            it.copy(
                selectedLine = index,
                candidates = emptyList(),
                selectedCandidate = 0,
                cardAtTop = cardAtTop(line.rect, current.frame?.height ?: 0),
                expanded = false,
            )
        }
        selectionJob = viewModelScope.launch {
            val words = try {
                repository.scanAt(line.text, offset)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
            _state.update { it.copy(candidates = words, selectedCandidate = 0) }
        }
    }

    fun selectCandidate(index: Int) {
        if (index !in _state.value.candidates.indices) return
        _state.update { it.copy(selectedCandidate = index) }
    }

    fun clearSelection() {
        selectionJob?.cancel()
        _state.update {
            it.copy(selectedLine = -1, candidates = emptyList(), selectedCandidate = 0, cardAtTop = false)
        }
    }

    fun toggleReader() {
        val reader = !_state.value.reader
        selectionJob?.cancel()
        _state.update {
            it.copy(
                reader = reader,
                candidates = emptyList(),
                selectedLine = -1,
                selectedCandidate = 0,
                cardAtTop = false,
                expanded = false,
            )
        }
        if (reader) readPage()
    }

    /** Reader mode: every line, segmented once, so any word on the page is reachable. */
    private fun readPage() {
        readerJob?.cancel()
        _state.update { it.copy(readingPage = true, readerLines = emptyList()) }
        readerJob = viewModelScope.launch {
            val lines = _state.value.lines
            val segmented = ArrayList<ReaderLine>(lines.size)
            for (line in lines) {
                val words = try {
                    repository.scan(line.text)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    emptyList()
                }
                segmented.add(ReaderLine(line.text, line.rect, words))
                _state.update { it.copy(readerLines = segmented.toList()) }
            }
            _state.update { it.copy(readingPage = false, readerLines = segmented) }
        }
    }

    fun selectReaderWord(lineIndex: Int, word: ScanWord) {
        selectionJob?.cancel()
        _state.update {
            it.copy(selectedLine = lineIndex, candidates = listOf(word), selectedCandidate = 0, cardAtTop = false)
        }
    }

    fun expand() {
        if (_state.value.entries.isNotEmpty()) _state.update { it.copy(expanded = true) }
    }

    fun collapse() {
        _state.update { it.copy(expanded = false) }
    }
}

/**
 * A captured window that hides its pixels — a banking app, DRM video, the lock
 * screen — comes back as one flat, usually black, color. OCR would report no text,
 * which is true but misleading, so the reader says the screen is protected instead.
 */
internal fun Bitmap.isProtected(): Boolean {
    if (width <= 0 || height <= 0) return true
    val stepX = (width / SAMPLE_COLUMNS).coerceAtLeast(1)
    val stepY = (height / SAMPLE_COLUMNS).coerceAtLeast(1)
    var first = 0
    var sampled = 0
    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            val pixel = getPixel(x, y)
            if (sampled == 0) first = pixel else if (pixel != first) return false
            sampled++
            x += stepX
        }
        y += stepY
    }
    val red = (first shr 16) and 0xFF
    val green = (first shr 8) and 0xFF
    val blue = first and 0xFF
    return red < DARK_THRESHOLD && green < DARK_THRESHOLD && blue < DARK_THRESHOLD
}

private const val SAMPLE_COLUMNS = 16
private const val DARK_THRESHOLD = 16
