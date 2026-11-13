package com.elianiva.jisholens.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.SearchOff
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elianiva.jisholens.data.AppContainer
import com.elianiva.jisholens.dictionary.Glossary
import com.elianiva.jisholens.domain.Entry
import com.elianiva.jisholens.domain.KanjiDetail
import com.elianiva.jisholens.domain.KanjiStats
import com.elianiva.jisholens.ui.components.CompactEntryCard
import com.elianiva.jisholens.ui.components.EmptyState
import com.elianiva.jisholens.ui.components.NoteEditorDialog
import com.elianiva.jisholens.ui.components.TagChip
import com.elianiva.jisholens.ui.components.rememberTextSpeaker
import com.elianiva.jisholens.ui.theme.JapaneseLocale

@Composable
fun KanjiScreen(
    container: AppContainer,
    character: String,
    onBack: () -> Unit,
    onOpenWord: (String) -> Unit,
    onOpenKanji: ((Char) -> Unit)? = null,
) {
    val viewModel: KanjiViewModel = viewModel(
        key = character,
        factory = viewModelFactory {
            initializer {
                KanjiViewModel(container.repository, container.notes, character)
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notes by viewModel.savedNotes.collectAsStateWithLifecycle()
    val speaker = rememberTextSpeaker()
    KanjiContent(
        state = state,
        onBack = onBack,
        onOpenWord = onOpenWord,
        onOpenKanji = onOpenKanji,
        notes = notes,
        onSaveNote = viewModel::saveNote,
        onSpeak = speaker?.let { voice -> { word: String -> voice.speak(word) } },
    )
}

@Composable
fun KanjiContent(
    state: KanjiUiState,
    onBack: () -> Unit,
    onOpenWord: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenKanji: ((Char) -> Unit)? = null,
    notes: Map<String, String> = emptyMap(),
    onSaveNote: ((String, String) -> Unit)? = null,
    onSpeak: ((String) -> Unit)? = null,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Kanji") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val detail = state.detail
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
            detail == null -> EmptyState(
                polygon = MaterialShapes.Cookie9Sided,
                icon = Icons.Rounded.SearchOff,
                title = "No kanji entry",
                body = state.error ?: "Import KANJIDIC in Dictionaries to see readings, meanings, and statistics.",
                modifier = Modifier.padding(padding).padding(top = 32.dp),
            )
            else -> KanjiBody(
                detail = detail,
                words = state.words,
                onOpenWord = onOpenWord,
                onOpenKanji = onOpenKanji,
                notes = notes,
                onSaveNote = onSaveNote,
                onSpeak = onSpeak,
                contentPadding = padding,
            )
        }
    }
}

@Composable
private fun KanjiBody(
    detail: KanjiDetail,
    words: List<Entry>,
    onOpenWord: (String) -> Unit,
    onOpenKanji: ((Char) -> Unit)?,
    notes: Map<String, String>,
    onSaveNote: ((String, String) -> Unit)?,
    onSpeak: ((String) -> Unit)?,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { KanjiHero(detail) }
        item { ReadingsCard(detail) }
        item { MeaningCard(detail) }
        item { StatsCard(detail.stats) }
        if (words.isNotEmpty()) {
            item {
                Heading("Words with ${detail.character}")
            }
            items(words, key = { "${it.termId}:${it.dictionaryId}" }) { entry ->
                var editingNote by remember(entry.expression) { mutableStateOf(false) }
                val note = notes[entry.expression].orEmpty()
                CompactEntryCard(
                    entry = entry,
                    onExpand = { onOpenWord(entry.expression) },
                    onKanji = onOpenKanji,
                    onSpeak = onSpeak?.let { speak -> { speak(entry.expression) } },
                    note = note,
                    onEditNote = if (onSaveNote != null) ({ editingNote = true }) else null,
                    expandDescription = "Look up ${entry.expression}",
                )
                if (editingNote) {
                    NoteEditorDialog(
                        note = note,
                        expression = entry.expression,
                        onDismiss = { editingNote = false },
                        onSave = { value ->
                            onSaveNote?.invoke(entry.expression, value)
                            editingNote = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun KanjiHero(detail: KanjiDetail) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = detail.character,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 112.sp,
                    lineHeight = 124.sp,
                    fontWeight = FontWeight.Medium,
                    localeList = JapaneseLocale,
                ),
            )
            if (detail.dictionaryTitle.isNotBlank()) {
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(detail.dictionaryTitle, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun ReadingsCard(detail: KanjiDetail) {
    if (detail.onyomi.isEmpty() && detail.kunyomi.isEmpty()) return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Readings", style = MaterialTheme.typography.titleSmallEmphasized, color = MaterialTheme.colorScheme.primary)
            if (detail.onyomi.isNotEmpty()) ReadingRow("On", detail.onyomi)
            if (detail.kunyomi.isNotEmpty()) ReadingRow("Kun", detail.kunyomi)
        }
    }
}

@Composable
private fun ReadingRow(label: String, readings: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Text(
            text = label,
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            readings.forEach { reading ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Text(
                        text = reading,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.titleMedium.copy(localeList = JapaneseLocale),
                    )
                }
            }
        }
    }
}

@Composable
private fun MeaningCard(detail: KanjiDetail) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Meaning", style = MaterialTheme.typography.titleSmallEmphasized, color = MaterialTheme.colorScheme.primary)
            Text(
                text = Glossary.text(detail.meanings),
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyLarge.copy(localeList = JapaneseLocale),
            )
            if (detail.tags.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    detail.tags.forEach { tag -> TagChip(tag = tag, onClick = {}) }
                }
            }
        }
    }
}

@Composable
private fun StatsCard(stats: KanjiStats) {
    if (stats.isEmpty) return
    val tiles = buildList {
        stats.grade?.let { add("Grade" to "$it") }
        stats.jlpt?.let { add("JLPT" to "Level $it") }
        stats.frequency?.let { add("Frequency" to "#$it") }
        stats.strokes?.let { add("Strokes" to "$it") }
        stats.radical?.let { add("Radical" to it) }
    }
    Column {
        Heading("Details")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tiles.forEach { (label, value) -> StatTile(label, value) }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLargeEmphasized, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp),
        style = MaterialTheme.typography.titleSmallEmphasized,
        color = MaterialTheme.colorScheme.primary,
    )
}
