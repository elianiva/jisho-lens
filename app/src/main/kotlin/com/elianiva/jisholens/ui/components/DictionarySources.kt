package com.elianiva.jisholens.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.elianiva.jisholens.data.DictionaryCatalog
import com.elianiva.jisholens.data.DictionarySource

/**
 * The catalogue a reader browses: definitions, kanji, ranks, pitch, and the specialised
 * extras, each entry with its licence and one action that suits its terms. A dictionary
 * the publisher allows the app to fetch gets a download button; the rest point at the
 * publisher's page and the reader imports the file.
 */
@Composable
fun DictionarySourceList(
    onDownload: (List<DictionarySource>) -> Unit,
    onOpenPage: (DictionarySource) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DictionaryCatalog.groups.forEach { (group, sources) ->
            if (sources.isEmpty()) return@forEach
            Text(
                text = group.label,
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp),
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            sources.forEach { source ->
                DictionarySourceCard(
                    source = source,
                    enabled = enabled,
                    onDownload = { onDownload(listOf(source)) },
                    onOpenPage = onOpenPage,
                )
            }
        }
    }
}

/**
 * Four entries that cover a lookup end to end: definitions, kanji, frequency, and pitch.
 * One tap queues every archive, so a new reader starts reading without choosing each one.
 */
@Composable
fun DictionaryStarterCard(
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val set = DictionaryCatalog.starterSet
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapeBadge(
                    polygon = MaterialShapes.Cookie9Sided,
                    containerColor = MaterialTheme.colorScheme.primary,
                    size = 56.dp,
                ) {
                    Icon(
                        Icons.Rounded.AutoStories,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Starter set", style = MaterialTheme.typography.titleLargeEmphasized)
                    Text(
                        text = "Terms, kanji, frequency, and pitch",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Text(
                text = set.joinToString(" · ") { it.title },
                modifier = Modifier.padding(top = 14.dp),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = "${formatBytes(set.sumOf { it.sizeBytes })} · each licence is listed below",
                modifier = Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
            Button(
                onClick = onDownload,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                enabled = enabled,
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Download the set")
            }
        }
    }
}

@Composable
private fun DictionarySourceCard(
    source: DictionarySource,
    enabled: Boolean,
    onDownload: () -> Unit,
    onOpenPage: (DictionarySource) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(start = 20.dp, top = 16.dp, end = 12.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = source.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
                KindChip(source.kind.label)
            }
            Text(
                text = "${formatBytes(source.sizeBytes)} · ${source.license}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = source.description,
                modifier = Modifier.padding(top = 8.dp, end = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = source.terms,
                modifier = Modifier.padding(top = 6.dp, end = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilledTonalButton(
                    onClick = { if (source.url != null) onDownload() else onOpenPage(source) },
                    enabled = enabled,
                ) {
                    Text(
                        if (source.url != null) {
                            "Download ${formatBytes(source.sizeBytes)}"
                        } else {
                            "Get the file"
                        },
                    )
                }
                if (source.url != null) {
                    TextButton(onClick = { onOpenPage(source) }, enabled = enabled) {
                        Text("Publisher page")
                    }
                }
            }
        }
    }
}

@Composable
private fun KindChip(label: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
