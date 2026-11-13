package com.elianiva.jisholens.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.elianiva.jisholens.domain.Coverage
import com.elianiva.jisholens.domain.Entry
import com.elianiva.jisholens.domain.ScanWord
import com.elianiva.jisholens.ui.theme.JapaneseLocale

/**
 * The candidates for one tapped word and the compact entry for the chosen one.
 *
 * The lens and the reader show the same sheet so a tap means the same thing in
 * both places: the chips offer the overlapping words, the card explains the
 * choice, and every action lives on one row.
 */
@Composable
fun CandidateSheet(
    candidates: List<ScanWord>,
    selectedCandidate: Int,
    knownWords: Set<String>,
    note: String,
    onSelectCandidate: (Int) -> Unit,
    onToggleKnown: () -> Unit,
    onExpand: () -> Unit,
    onClearSelection: () -> Unit,
    onCopy: (String) -> Unit,
    onMine: (Entry) -> Unit,
    modifier: Modifier = Modifier,
    onSpeak: ((String) -> Unit)? = null,
    onSpeakSentence: (() -> Unit)? = null,
    onSaveNote: ((String, String) -> Unit)? = null,
    coverage: Coverage? = null,
) {
    val entry = candidates.getOrNull(selectedCandidate)?.entries?.firstOrNull() ?: return
    val known = entry.expression in knownWords
    var editingNote by remember(entry.expression) { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (candidates.size > 1) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 3.dp,
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FlowRow(
                        modifier = Modifier.weight(1f, fill = false),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        candidates.forEachIndexed { index, candidate ->
                            FilterChip(
                                selected = index == selectedCandidate,
                                onClick = { onSelectCandidate(index) },
                                label = {
                                    Text(
                                        text = candidate.surface,
                                        style = MaterialTheme.typography.titleSmall.copy(localeList = JapaneseLocale),
                                    )
                                },
                            )
                        }
                    }
                    Text(
                        text = "${selectedCandidate + 1} of ${candidates.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        CompactEntryCard(
            entry = entry,
            onExpand = onExpand,
            note = note,
            onEditNote = if (onSaveNote != null) ({ editingNote = true }) else null,
            onSpeak = onSpeak?.let { speak -> { speak(entry.expression) } },
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            if (onSaveNote != null) {
                IconButton(onClick = { editingNote = true }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.StickyNote2,
                        contentDescription = if (note.isBlank()) {
                            "Add a note for ${entry.expression}"
                        } else {
                            "Edit the note for ${entry.expression}"
                        },
                        tint = if (note.isBlank()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }
            if (onSpeakSentence != null) {
                IconButton(onClick = onSpeakSentence) {
                    Icon(Icons.Rounded.RecordVoiceOver, contentDescription = "Read the sentence aloud")
                }
            }
            IconButton(onClick = { onMine(entry) }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.NoteAdd,
                    contentDescription = "Make an Anki note for ${entry.expression}",
                )
            }
            IconButton(onClick = onToggleKnown) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = if (known) "Unmark ${entry.expression} as known" else "Mark ${entry.expression} as known",
                    tint = if (known) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onCopy(entry.expression) }) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy the word")
            }
            IconButton(onClick = onClearSelection) {
                Icon(Icons.Rounded.Close, contentDescription = "Clear the selection")
            }
        }
        if (candidates.size > 1) {
            Text(
                text = "${candidates.size} entries for ${candidates[selectedCandidate].surface}",
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        coverage?.takeIf { it.total > 0 }?.let { page ->
            Text(
                text = "${page.percent}% of this page known",
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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

/** Every entry for the tapped word, with every gloss, tag, image, and link. */
@Composable
fun ExpandedEntrySheet(
    entries: List<Entry>,
    loadMedia: suspend (Long, String) -> ByteArray?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onQuery: (String) -> Unit = {},
    onKanji: ((Char) -> Unit)? = null,
    noteFor: (Entry) -> String = { "" },
    onSaveNote: ((String, String) -> Unit)? = null,
    onSpeak: ((String) -> Unit)? = null,
) {
    Surface(modifier, color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Text("Entry", style = MaterialTheme.typography.titleLargeEmphasized)
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(entries, key = { "${it.termId}:${it.dictionaryId}" }) { entry ->
                    EntryCard(
                        entry = entry,
                        loadMedia = loadMedia,
                        onQuery = onQuery,
                        onKanji = onKanji,
                        note = noteFor(entry),
                        onSaveNote = onSaveNote?.let { save -> { value -> save(entry.expression, value) } },
                        onSpeak = onSpeak?.let { speak -> { speak(entry.expression) } },
                    )
                }
            }
        }
    }
}
