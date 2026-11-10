package com.elianiva.jisholens.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
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
 * The dictionaries a new reader can start with, each with its size, its licence, and
 * one button that downloads and imports it. Downloading goes to the publisher, not to
 * a copy, so the reader can also see where the data comes from.
 */
@Composable
fun DictionarySourceList(
    onDownload: (DictionarySource) -> Unit,
    onOpenPage: (DictionarySource) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DictionaryCatalog.sources.forEach { source ->
            DictionarySourceCard(
                source = source,
                onDownload = onDownload,
                onOpenPage = onOpenPage,
            )
        }
    }
}

@Composable
private fun DictionarySourceCard(
    source: DictionarySource,
    onDownload: (DictionarySource) -> Unit,
    onOpenPage: (DictionarySource) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(start = 20.dp, top = 16.dp, end = 12.dp, bottom = 8.dp)) {
            Text(source.title, style = MaterialTheme.typography.titleMediumEmphasized)
            Text(
                text = "${source.sizeMegabytes} MB · ${source.license}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = source.description,
                modifier = Modifier.padding(top = 8.dp, end = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilledTonalButton(onClick = { onDownload(source) }) {
                    Text("Download ${source.sizeMegabytes} MB")
                }
                TextButton(onClick = { onOpenPage(source) }) { Text("Publisher page") }
            }
        }
    }
}
