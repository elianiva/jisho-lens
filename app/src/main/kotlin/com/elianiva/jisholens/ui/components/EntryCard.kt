package com.elianiva.jisholens.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.SubdirectoryArrowRight
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elianiva.jisholens.domain.Entry
import com.elianiva.jisholens.domain.EntryTag
import com.elianiva.jisholens.ui.theme.JapaneseLocale
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull

/**
 * One dictionary entry on a single tonal container: the headword leads with its furigana in the
 * accent color, tags and frequency sit beneath it, and the definitions run at body-large size.
 */
@Composable
fun EntryCard(
    entry: Entry,
    loadMedia: suspend (dictionaryId: Long, path: String) -> ByteArray?,
    onQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTag by remember { mutableStateOf<EntryTag?>(null) }
    Surface(
        modifier = modifier.fillMaxWidth().animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(start = 24.dp, top = 20.dp, end = 24.dp, bottom = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                RubyText(
                    segments = entry.ruby,
                    modifier = Modifier.weight(1f),
                    baseStyle = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Medium),
                    readingStyle = MaterialTheme.typography.labelLarge,
                )
                if (entry.frequency != null) FrequencyPill(entry.frequency)
            }
            if (entry.reading.isNotBlank() && entry.reading != entry.expression &&
                entry.ruby.none { it.reading != null }
            ) {
                Text(
                    text = entry.reading,
                    style = MaterialTheme.typography.titleMedium.copy(localeList = JapaneseLocale),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (entry.deinflection != null) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SubdirectoryArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "From ${entry.deinflection}",
                        style = MaterialTheme.typography.labelLarge.copy(localeList = JapaneseLocale),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (entry.tags.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    entry.tags.forEach { tag ->
                        TagChip(tag = tag, onClick = { selectedTag = it })
                    }
                }
            }

            if (entry.glossary != JsonNull && entry.glossary != JsonArray(emptyList())) {
                GlossaryContent(
                    element = entry.glossary,
                    dictionaryId = entry.dictionaryId,
                    loadMedia = loadMedia,
                    onQuery = onQuery,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            if (entry.dictionaryTitle.isNotBlank()) {
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = entry.dictionaryTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
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

@Composable
private fun FrequencyPill(value: Double) {
    val text = formatFrequency(value)
    Surface(
        modifier = Modifier.semantics { contentDescription = "Frequency $text" },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Rounded.TrendingUp, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun formatFrequency(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
