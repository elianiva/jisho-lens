package com.elianiva.jisholens.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.elianiva.jisholens.data.ComponentState
import com.elianiva.jisholens.data.ComponentStatus

/**
 * The components that are still arriving, floating above the navigation bar on
 * every screen. It goes away on its own when each one is ready.
 */
@Composable
fun ComponentDownloadPill(
    statuses: List<ComponentStatus>,
    onRetry: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = statuses.filter {
        it.state is ComponentState.Downloading || it.state is ComponentState.Failed
    }
    AnimatedVisibility(
        visible = active.isNotEmpty(),
        modifier = modifier,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                active.forEach { status -> DownloadRow(status, onRetry) }
            }
        }
    }
}

@Composable
private fun DownloadRow(status: ComponentStatus, onRetry: (String) -> Unit) {
    val state = status.state
    val failed = state is ComponentState.Failed
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = if (failed) Icons.Rounded.ErrorOutline else Icons.Rounded.Download,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f)) {
                Text(status.title, style = MaterialTheme.typography.titleSmallEmphasized)
                if (failed) {
                    Text(
                        text = "Download failed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when (state) {
                is ComponentState.Downloading -> state.percent?.let { percent ->
                    Text(
                        text = "$percent%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ComponentState.Failed -> TextButton(onClick = { onRetry(status.id) }) { Text("Retry") }
                else -> Unit
            }
        }
        if (state is ComponentState.Downloading) {
            val percent = state.percent
            if (percent == null) {
                LinearWavyProgressIndicator(Modifier.fillMaxWidth())
            } else {
                LinearWavyProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
