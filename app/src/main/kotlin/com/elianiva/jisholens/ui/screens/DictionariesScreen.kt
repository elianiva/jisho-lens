package com.elianiva.jisholens.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elianiva.jisholens.data.AppContainer
import com.elianiva.jisholens.data.ImportService
import com.elianiva.jisholens.data.ImportState
import com.elianiva.jisholens.dictionary.DictionaryRecord
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionariesScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dictionaries by container.dictionaries.collectAsStateWithLifecycle()
    val importState by container.importController.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<DictionaryRecord?>(null) }
    var pendingUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        pendingUri?.let { uri ->
            ImportService.start(context, uri)
            pendingUri = null
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            if (needsPermission) {
                pendingUri = uri
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                ImportService.start(context, uri)
            }
        }
    }

    LaunchedEffect(Unit) { container.refreshDictionaries() }
    LaunchedEffect(importState) {
        when (val state = importState) {
            is ImportState.Done -> {
                container.refreshDictionaries()
                container.settings.setOnboardingComplete(true)
                snackbar.showSnackbar("Imported ${state.summary.title}")
            }
            is ImportState.Failed -> snackbar.showSnackbar(state.message)
            ImportState.Cancelled -> snackbar.showSnackbar("Import cancelled")
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dictionaries") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Outlined.Add, contentDescription = "Import dictionary")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { ImportStatus(importState, onCancel = container.importController::requestCancel) }

            if (dictionaries.isEmpty()) {
                item {
                    Text(
                        "No dictionaries yet. Import a Yomitan .zip file from your storage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = { picker.launch(arrayOf("*/*")) },
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Text("Choose a .zip file")
                    }
                }
            } else {
                item {
                    Text(
                        "Stored database: ${formatBytes(container.fileSize())}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(dictionaries, key = { it.id }) { dictionary ->
                    DictionaryRow(
                        dictionary = dictionary,
                        canMoveUp = dictionary.id != dictionaries.first().id,
                        canMoveDown = dictionary.id != dictionaries.last().id,
                        onEnabled = { enabled ->
                            scope.launch {
                                container.repository.setEnabled(dictionary.id, enabled)
                                container.refreshDictionaries()
                            }
                        },
                        onMove = { delta ->
                            scope.launch {
                                container.repository.move(dictionary.id, delta)
                                container.refreshDictionaries()
                            }
                        },
                        onDelete = { pendingDelete = dictionary },
                    )
                }
            }
        }
    }

    pendingDelete?.let { dictionary ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove ${dictionary.title}?") },
            text = { Text("This deletes the imported dictionary and reclaims its storage.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            container.repository.delete(dictionary.id)
                            container.refreshDictionaries()
                            pendingDelete = null
                        }
                    },
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun DictionaryRow(
    dictionary: DictionaryRecord,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEnabled: (Boolean) -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(dictionary.title, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        buildString {
                            append(dictionary.revision.ifBlank { "unknown revision" })
                            dictionary.counts["terms"]?.let { append(" · $it terms") }
                            dictionary.counts["kanji"]?.let { append(" · $it kanji") }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = dictionary.enabled, onCheckedChange = onEnabled)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
                    Icon(Icons.Outlined.ArrowUpward, contentDescription = "Move up")
                }
                IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
                    Icon(Icons.Outlined.ArrowDownward, contentDescription = "Move down")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove dictionary")
                }
            }
        }
    }
}

@Composable
private fun ImportStatus(state: ImportState, onCancel: () -> Unit) {
    when (state) {
        ImportState.Idle -> Unit
        is ImportState.Copying -> Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Text("Copying ${state.name}", style = MaterialTheme.typography.bodyMedium)
                val total = state.total
                if (total != null && total > 0) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        progress = { state.bytes.toFloat() / total.toFloat() },
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }
        }
        is ImportState.Importing -> Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.padding(end = 12.dp), strokeWidth = 2.dp)
                    Column(Modifier.weight(1f)) {
                        Text(state.title, style = MaterialTheme.typography.bodyMedium)
                        Text(state.message, style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
            }
        }
        is ImportState.Done -> Text(
            "Imported ${state.summary.title} in ${state.summary.durationMillis / 1000}s",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        is ImportState.Failed -> Text(
            state.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        ImportState.Cancelled -> Text(
            "Import cancelled",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> "%.0f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
