package com.elianiva.jisholens.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.elianiva.jisholens.data.DictionaryRepository
import com.elianiva.jisholens.domain.Entry
import com.elianiva.jisholens.domain.EntryTag

@Composable
fun EntryCard(
    entry: Entry,
    repository: DictionaryRepository,
    onQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTag by remember { mutableStateOf<EntryTag?>(null) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                RubyText(segments = entry.ruby)
                Column(horizontalAlignment = Alignment.End) {
                    if (entry.frequency != null) {
                        Text(
                            text = formatFrequency(entry.frequency),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (entry.dictionaryTitle.isNotBlank()) {
                        Text(
                            text = entry.dictionaryTitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            if (entry.reading.isNotBlank() && entry.reading != entry.expression &&
                entry.ruby.none { it.reading != null }
            ) {
                Text(
                    text = entry.reading,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (entry.deinflection != null) {
                Text(
                    text = "From ${entry.deinflection}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (entry.tags.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    entry.tags.forEach { tag ->
                        TagChip(tag = tag, onClick = { selectedTag = it })
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            GlossaryContent(
                element = entry.glossary,
                dictionaryId = entry.dictionaryId,
                repository = repository,
                onQuery = onQuery,
            )
        }
    }

    selectedTag?.let { tag ->
        AlertDialog(
            onDismissRequest = { selectedTag = null },
            title = { Text(tag.name) },
            text = { Text(tag.notes.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { selectedTag = null }) { Text("Close") }
            },
        )
    }
}

private fun formatFrequency(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
