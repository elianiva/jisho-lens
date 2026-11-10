package com.elianiva.jisholens.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.elianiva.jisholens.data.ImportState

/**
 * Where an import is, in the expressive progress vocabulary: a morphing loading indicator for
 * "working", a wavy bar for how far along, and a plain container for how it ended.
 */
@Composable
fun ImportProgressCard(
    state: ImportState,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        ImportState.Idle -> Unit
        is ImportState.Copying -> ProgressSurface(modifier) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LoadingIndicator(Modifier.size(48.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                Column(Modifier.weight(1f)) {
                    Text("Copying", style = MaterialTheme.typography.titleMediumEmphasized)
                    Text(
                        text = state.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
                }
            }
            val total = state.total
            if (total != null && total > 0) {
                LinearWavyProgressIndicator(
                    progress = { (state.bytes.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
                )
                Text(
                    text = "${formatBytes(state.bytes)} of ${formatBytes(total)}",
                    style = MaterialTheme.typography.labelLarge,
                )
            } else {
                LinearWavyProgressIndicator(
                Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
            )
            }
        }
        is ImportState.Downloading -> ProgressSurface(modifier) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LoadingIndicator(Modifier.size(48.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                Column(Modifier.weight(1f)) {
                    Text("Downloading", style = MaterialTheme.typography.titleMediumEmphasized)
                    Text(text = state.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                }
            }
            val total = state.total
            if (total != null && total > 0) {
                LinearWavyProgressIndicator(
                    progress = { (state.bytes.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
                )
                Text(
                    text = "${formatBytes(state.bytes)} of ${formatBytes(total)}",
                    style = MaterialTheme.typography.labelLarge,
                )
            } else {
                LinearWavyProgressIndicator(
                    Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
                )
            }
        }
        is ImportState.Importing -> ProgressSurface(modifier) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LoadingIndicator(Modifier.size(48.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Importing",
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
                }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
            LinearWavyProgressIndicator(
                Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
            )
            Text(
                text = state.message,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
            )
        }
        is ImportState.Done -> ResultSurface(
            modifier = modifier,
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer,
            icon = { Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(32.dp)) },
            title = "Imported ${state.summary.title}",
            detail = "Done in ${state.summary.durationMillis / 1000}s",
        )
        is ImportState.Failed -> ResultSurface(
            modifier = modifier,
            container = MaterialTheme.colorScheme.errorContainer,
            content = MaterialTheme.colorScheme.onErrorContainer,
            icon = { Icon(Icons.Rounded.ErrorOutline, contentDescription = null, modifier = Modifier.size(32.dp)) },
            title = "Import failed",
            detail = state.message,
        )
        ImportState.Cancelled -> ResultSurface(
            modifier = modifier,
            container = MaterialTheme.colorScheme.surfaceContainerHigh,
            content = MaterialTheme.colorScheme.onSurface,
            icon = { Icon(Icons.Rounded.ErrorOutline, contentDescription = null, modifier = Modifier.size(32.dp)) },
            title = "Import cancelled",
            detail = null,
        )
    }
}

@Composable
private fun ProgressSurface(modifier: Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
        }
    }
}

@Composable
private fun ResultSurface(
    modifier: Modifier,
    container: Color,
    content: Color,
    icon: @Composable () -> Unit,
    title: String,
    detail: String?,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            icon()
            Column {
                Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
                if (detail != null) Text(detail, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> "%.0f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
