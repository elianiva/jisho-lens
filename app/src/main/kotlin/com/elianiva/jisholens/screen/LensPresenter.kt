package com.elianiva.jisholens.screen

import android.graphics.Rect
import com.elianiva.jisholens.data.DictionaryRepository
import com.elianiva.jisholens.data.SettingsRepository
import com.elianiva.jisholens.domain.Entry
import com.elianiva.jisholens.domain.ScanWord
import com.elianiva.jisholens.domain.headword
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why the lens has nothing to show. Each one has a way forward. */
enum class LensMessage {
    /** No Japanese in the accessibility tree and none found in the image. */
    NoText,

    /** The active window hides its pixels from screenshots. */
    Protected,

    /** This Android version cannot screenshot from an accessibility service. */
    OcrUnavailable,

    /** Reading the screen failed. */
    Failed,
}

data class LensUiState(
    val lines: List<LensLine> = emptyList(),
    val scanning: Boolean = true,
    val message: LensMessage? = null,
    val selectedLine: Int = -1,
    val candidates: List<ScanWord> = emptyList(),
    val selectedCandidate: Int = 0,
    val expanded: Boolean = false,
    val lookingUp: Boolean = false,
    val knownWords: Set<String> = emptySet(),
    val canReadImage: Boolean = false,
    val fromImage: Boolean = false,
) {
    val line: LensLine? get() = lines.getOrNull(selectedLine)
    val word: ScanWord? get() = candidates.getOrNull(selectedCandidate)
    val entries: List<Entry> get() = word?.entries.orEmpty()

    /** The selected word's box on screen, for the highlight under the entry sheet. */
    val wordBox: Rect?
        get() {
            val word = word ?: return null
            return line?.wordRect(word.start, word.end)
        }
}

/**
 * Holds the live lens: the text currently on screen, the word being looked up, and
 * the ten-minute idle stop. It reads the tree again on every rescan and whenever the
 * app under the lens changes, so the page stays the source of truth instead of a
 * frozen frame.
 */
class LensPresenter(
    private val repository: DictionaryRepository,
    private val settings: SettingsRepository,
    private val reader: LensNodeReader,
    private val onDismiss: () -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(LensUiState(canReadImage = reader.canReadImages))
    val state: StateFlow<LensUiState> = _state.asStateFlow()

    private var readJob: Job? = null
    private var selectionJob: Job? = null
    private var idleJob: Job? = null
    private var selectionToken = 0

    init {
        scope.launch {
            settings.knownWords.collect { known -> _state.update { it.copy(knownWords = known) } }
        }
    }

    fun start() {
        touch()
        read(forceImage = false)
    }

    fun close() {
        readJob?.cancel()
        selectionJob?.cancel()
        idleJob?.cancel()
        scope.cancel()
    }

    fun rescan() {
        touch()
        read(forceImage = false)
    }

    fun readImage() {
        touch()
        if (!_state.value.canReadImage) return
        read(forceImage = true)
    }

    /** A window change: re-read the tree when no entry is open, so highlights follow the page. */
    fun refresh() {
        val current = _state.value
        if (current.scanning || current.expanded || current.selectedLine >= 0) return
        read(forceImage = false, keepLines = true)
    }

    /** The app under the lens changed: the old selection belongs to another window. */
    fun onWindowChanged() {
        clearSelection()
        refresh()
    }

    fun scroll(forward: Boolean) {
        if (_state.value.expanded) return
        touch()
        scope.launch {
            if (reader.scroll(forward)) {
                delay(SCROLL_SETTLE_MILLIS)
                read(forceImage = false, keepLines = true)
            }
        }
    }

    fun onTap(imageX: Float, imageY: Float) {
        touch()
        val lines = _state.value.lines
        val index = lensLineAt(lines, imageX, imageY)
        if (index < 0) {
            clearSelection()
            return
        }
        val line = lines[index]
        val offset = line.nearestWordOffset(line.characterAt(imageX, imageY))
        val token = ++selectionToken
        selectionJob?.cancel()
        _state.update {
            it.copy(
                selectedLine = index,
                candidates = emptyList(),
                selectedCandidate = 0,
                expanded = false,
                lookingUp = true,
            )
        }
        selectionJob = scope.launch {
            val words = try {
                repository.scanAt(line.text, offset)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
            if (token != selectionToken) return@launch
            _state.update {
                it.copy(candidates = words, selectedCandidate = 0, lookingUp = false)
            }
        }
    }

    fun selectCandidate(index: Int) {
        if (index !in _state.value.candidates.indices) return
        touch()
        _state.update { it.copy(selectedCandidate = index) }
    }

    fun clearSelection() {
        selectionToken++
        selectionJob?.cancel()
        _state.update {
            it.copy(
                selectedLine = -1,
                candidates = emptyList(),
                selectedCandidate = 0,
                expanded = false,
                lookingUp = false,
            )
        }
    }

    fun toggleKnown() {
        val expression = _state.value.word?.headword() ?: return
        touch()
        val known = expression in _state.value.knownWords
        scope.launch { settings.setKnown(expression, !known) }
    }

    fun expand() {
        if (_state.value.entries.isEmpty()) return
        touch()
        _state.update { it.copy(expanded = true) }
    }

    fun collapse() {
        touch()
        _state.update { it.copy(expanded = false) }
    }

    private fun read(forceImage: Boolean, keepLines: Boolean = false) {
        readJob?.cancel()
        selectionToken++
        _state.update {
            it.copy(
                scanning = true,
                message = null,
                selectedLine = -1,
                candidates = emptyList(),
                selectedCandidate = 0,
                expanded = false,
                lookingUp = false,
                lines = if (keepLines) it.lines else emptyList(),
            )
        }
        readJob = scope.launch {
            when (val reading = reader.read(forceImage)) {
                is LensReading.Lines -> _state.update {
                    it.copy(
                        lines = reading.lines,
                        scanning = false,
                        message = null,
                        fromImage = reading.fromImage,
                        canReadImage = reader.canReadImages,
                    )
                }
                LensReading.NoText -> _state.update {
                    it.copy(
                        lines = emptyList(),
                        scanning = false,
                        lookingUp = false,
                        message = LensMessage.NoText,
                    )
                }
                LensReading.Protected -> _state.update {
                    it.copy(lines = emptyList(), scanning = false, lookingUp = false, message = LensMessage.Protected)
                }
                LensReading.OcrUnavailable -> _state.update {
                    it.copy(lines = emptyList(), scanning = false, lookingUp = false, message = LensMessage.OcrUnavailable)
                }
                LensReading.Failed -> _state.update {
                    it.copy(lines = emptyList(), scanning = false, lookingUp = false, message = LensMessage.Failed)
                }
            }
        }
    }

    private fun touch() {
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(IDLE_TIMEOUT_MILLIS)
            onDismiss()
        }
    }

    private companion object {
        const val IDLE_TIMEOUT_MILLIS = 10 * 60 * 1000L
        const val SCROLL_SETTLE_MILLIS = 250L
    }
}
