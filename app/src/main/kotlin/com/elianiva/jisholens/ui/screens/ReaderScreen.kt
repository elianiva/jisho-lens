package com.elianiva.jisholens.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elianiva.jisholens.data.AppContainer
import com.elianiva.jisholens.data.buildAnkiNote
import com.elianiva.jisholens.data.mineToAnki
import com.elianiva.jisholens.domain.Entry
import com.elianiva.jisholens.domain.ScanWord
import com.elianiva.jisholens.ui.components.CandidateSheet
import com.elianiva.jisholens.ui.components.EmptyState
import com.elianiva.jisholens.ui.components.ExpandedEntrySheet
import com.elianiva.jisholens.ui.components.LargeActionButton
import com.elianiva.jisholens.ui.components.rememberTextSpeaker
import com.elianiva.jisholens.ui.theme.JapaneseLocale

@Composable
fun ReaderScreen(
    container: AppContainer,
    onOpenKanji: ((Char) -> Unit)? = null,
) {
    val context = LocalContext.current
    val viewModel: ReaderViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                ReaderViewModel(context.applicationContext, container.repository, container.settings, container.notes)
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val knownWords by viewModel.knownWords.collectAsStateWithLifecycle()
    val notes by viewModel.savedNotes.collectAsStateWithLifecycle()
    val speaker = rememberTextSpeaker()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::import)
    }

    ReaderContent(
        state = state,
        knownWords = knownWords,
        notes = notes,
        loadMedia = container.repository::media,
        onOpenFile = { picker.launch(arrayOf("*/*")) },
        onTap = viewModel::tap,
        onSelectCandidate = viewModel::selectCandidate,
        onToggleKnown = viewModel::toggleKnown,
        onExpand = viewModel::expand,
        onCollapse = viewModel::collapse,
        onClearSelection = viewModel::clearSelection,
        onCopy = { word ->
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard?.setPrimaryClip(ClipData.newPlainText("Jisho Lens", word))
        },
        onMine = { entry ->
            runCatching { context.mineToAnki(buildAnkiNote(entry, state.line)) }
        },
        onSaveNote = viewModel::saveNote,
        onSpeak = speaker?.let { voice -> { word: String -> voice.speak(word) } },
        onOpenKanji = onOpenKanji,
    )
}

/** The imported file as tappable lines, with the selected word's sheet beneath them. */
@Composable
fun ReaderContent(
    state: ReaderUiState,
    knownWords: Set<String>,
    notes: Map<String, String>,
    loadMedia: suspend (Long, String) -> ByteArray?,
    onOpenFile: () -> Unit,
    onTap: (line: Int, offset: Int) -> Unit,
    onSelectCandidate: (Int) -> Unit,
    onToggleKnown: () -> Unit,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onClearSelection: () -> Unit,
    onCopy: (String) -> Unit,
    onMine: (Entry) -> Unit,
    modifier: Modifier = Modifier,
    onSaveNote: ((String, String) -> Unit)? = null,
    onSpeak: ((String) -> Unit)? = null,
    onOpenKanji: ((Char) -> Unit)? = null,
) {
    BackHandler(enabled = state.expanded || state.selectedLine >= 0) {
        if (state.expanded) onCollapse() else onClearSelection()
    }

    if (state.expanded) {
        ExpandedEntrySheet(
            entries = state.entries,
            loadMedia = loadMedia,
            onBack = onCollapse,
            onKanji = onOpenKanji,
            noteFor = { entry -> notes[entry.expression].orEmpty() },
            onSaveNote = onSaveNote,
            onSpeak = onSpeak,
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.fileName ?: "Read",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    IconButton(onClick = onOpenFile) {
                        Icon(Icons.Rounded.FileOpen, contentDescription = "Open a text or subtitle file")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> LoadingIndicator(Modifier.align(Alignment.Center))
                state.lines.isEmpty() -> EmptyState(
                    polygon = MaterialShapes.Clover4Leaf,
                    icon = Icons.AutoMirrored.Rounded.MenuBook,
                    title = "Open something to read",
                    body = state.error ?: "Plain text, SubRip, and WebVTT stay on this device.",
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 32.dp),
                    action = {
                        LargeActionButton("Open a file", Icons.Rounded.FileOpen, onClick = onOpenFile)
                    },
                )
                else -> Column(Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        itemsIndexed(state.lines) { index, line ->
                            ReaderLine(
                                text = line,
                                selected = index == state.selectedLine,
                                onTap = { offset -> onTap(index, offset) },
                            )
                        }
                    }
                    AnimatedVisibility(
                        visible = state.selectedLine >= 0,
                        enter = slideInVertically(initialOffsetY = { it / 4 }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it / 4 }) + fadeOut(),
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = MaterialTheme.shapes.extraLarge,
                            tonalElevation = 3.dp,
                        ) {
                            if (state.candidates.isNotEmpty()) {
                                CandidateSheet(
                                    candidates = state.candidates,
                                    selectedCandidate = state.selectedCandidate,
                                    knownWords = knownWords,
                                    note = state.entries.firstOrNull()?.let { entry -> notes[entry.expression] }.orEmpty(),
                                    onSelectCandidate = onSelectCandidate,
                                    onToggleKnown = onToggleKnown,
                                    onExpand = onExpand,
                                    onClearSelection = onClearSelection,
                                    onCopy = onCopy,
                                    onMine = onMine,
                                    onSpeak = onSpeak,
                                    onSpeakSentence = state.line
                                        ?.takeIf { onSpeak != null }
                                        ?.let { sentence -> { onSpeak?.invoke(sentence) } },
                                    onSaveNote = onSaveNote,
                                    modifier = Modifier.padding(12.dp),
                                )
                            } else {
                                RowNoEntry(line = state.line.orEmpty(), onClear = onClearSelection)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderLine(
    text: String,
    selected: Boolean,
    onTap: (Int) -> Unit,
) {
    var layout by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    Surface(
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .pointerInput(text) {
                    detectTapGestures { position ->
                        layout?.let { onTap(it.getOffsetForPosition(position)) }
                    }
                },
            style = MaterialTheme.typography.bodyLarge.copy(localeList = JapaneseLocale, lineHeight = 28.sp),
            onTextLayout = { layout = it },
        )
    }
}

@Composable
private fun RowNoEntry(line: String, onClear: () -> Unit) {
    Column(Modifier.padding(20.dp)) {
        Text("No entry for this line", style = MaterialTheme.typography.titleMediumEmphasized)
        Text(
            text = line,
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodyMedium.copy(localeList = JapaneseLocale),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        androidx.compose.material3.TextButton(onClick = onClear) { Text("Clear") }
    }
}
