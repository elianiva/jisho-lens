package com.elianiva.jisholens.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elianiva.jisholens.data.AppContainer
import com.elianiva.jisholens.data.ImportState
import com.elianiva.jisholens.data.DictionaryCatalog
import com.elianiva.jisholens.data.DictionaryKind
import com.elianiva.jisholens.data.DictionarySource
import com.elianiva.jisholens.dictionary.DictionaryRecord
import com.elianiva.jisholens.ui.components.DictionaryShapes
import com.elianiva.jisholens.ui.components.DictionarySourceList
import com.elianiva.jisholens.ui.components.EmptyState
import com.elianiva.jisholens.ui.components.ImportProgressCard
import com.elianiva.jisholens.ui.components.LargeActionButton
import com.elianiva.jisholens.ui.components.ShapeBadge
import com.elianiva.jisholens.ui.components.segmentedItemColors
import com.elianiva.jisholens.ui.components.formatBytes
import com.elianiva.jisholens.ui.components.rememberDictionaryDownloader
import com.elianiva.jisholens.ui.components.rememberDictionaryImporter
import kotlinx.coroutines.launch

@Composable
fun DictionariesScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val dictionaries by container.dictionaries.collectAsStateWithLifecycle()
    val importState by container.importController.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<DictionaryRecord?>(null) }
    val context = LocalContext.current
    val importDictionary = rememberDictionaryImporter()
    val downloadDictionary = rememberDictionaryDownloader()

    LaunchedEffect(Unit) { container.refreshDictionaries() }
    LaunchedEffect(importState) {
        when (val state = importState) {
            is ImportState.Done -> {
                container.refreshDictionaries()
                container.settings.setOnboardingComplete(true)
                snackbar.showSnackbar(
                    if (state.count > 1) {
                        "Imported ${state.count} dictionaries"
                    } else {
                        "Imported ${state.summary.title}"
                    },
                )
            }
            is ImportState.Failed -> snackbar.showSnackbar(state.message)
            ImportState.Cancelled -> snackbar.showSnackbar("Import cancelled")
            else -> Unit
        }
    }

    DictionariesContent(
        dictionaries = dictionaries,
        importState = importState,
        storedBytes = container.fileSize(),
        snackbar = snackbar,
        onBack = onBack,
        onImport = importDictionary,
        onDownloadSource = downloadDictionary,
        onOpenSource = { source ->
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source.pageUrl)))
        },
        onCancelImport = container.importController::requestCancel,
        onEnabled = { dictionary, enabled ->
            scope.launch {
                container.repository.setEnabled(dictionary.id, enabled)
                container.refreshDictionaries()
            }
        },
        onMove = { dictionary, delta ->
            scope.launch {
                container.repository.move(dictionary.id, delta)
                container.refreshDictionaries()
            }
        },
        onDelete = { pendingDelete = it },
    )

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

/** Dictionaries and the import in flight. The add button is the main action, so it floats. */
@Composable
fun DictionariesContent(
    dictionaries: List<DictionaryRecord>,
    importState: ImportState,
    storedBytes: Long,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onImport: () -> Unit,
    onDownloadSource: (List<DictionarySource>) -> Unit,
    onOpenSource: (DictionarySource) -> Unit,
    onCancelImport: () -> Unit,
    onEnabled: (DictionaryRecord, Boolean) -> Unit,
    onMove: (DictionaryRecord, Int) -> Unit,
    onDelete: (DictionaryRecord) -> Unit,
) {
    var addMenu by remember { mutableStateOf(false) }
    var catalogue by remember { mutableStateOf(false) }
    val importing = importState is ImportState.Copying ||
        importState is ImportState.Downloading ||
        importState is ImportState.Importing
    val showCatalogue = (dictionaries.isEmpty() || catalogue) && !importing
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                scrollBehavior = scroll,
                title = { Text("Dictionaries") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            if (dictionaries.isNotEmpty() && !importing) {
                Box {
                    ExtendedFloatingActionButton(
                        onClick = { addMenu = true },
                        icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                        text = { Text("Add") },
                    )
                    DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Browse catalogue") },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Rounded.LibraryBooks, contentDescription = null)
                            },
                            onClick = {
                                addMenu = false
                                catalogue = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Import a .zip file") },
                            leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                            onClick = {
                                addMenu = false
                                onImport()
                            },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            if (importState !is ImportState.Idle) {
                item(key = "import") {
                    ImportProgressCard(
                        state = importState,
                        onCancel = onCancelImport,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                }
            }
            if (dictionaries.isEmpty()) {
                if (!importing) {
                    item(key = "empty") {
                        EmptyState(
                            polygon = MaterialShapes.Cookie9Sided,
                            icon = Icons.AutoMirrored.Rounded.LibraryBooks,
                            title = "No dictionaries yet",
                            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                            action = { LargeActionButton("Choose a .zip file", Icons.Rounded.Add, onClick = onImport) },
                        )
                    }
                    item(key = "sources") {
                        DictionarySourceList(
                            onDownload = onDownloadSource,
                            onOpenPage = onOpenSource,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }
            } else {
                if (showCatalogue) {
                    item(key = "catalogue") {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Dictionary catalogue",
                                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                                    style = MaterialTheme.typography.titleMediumEmphasized,
                                )
                                TextButton(onClick = { catalogue = false }) { Text("Hide") }
                            }
                            DictionarySourceList(
                                onDownload = onDownloadSource,
                                onOpenPage = onOpenSource,
                                modifier = Modifier.padding(bottom = 16.dp),
                                enabled = !importing,
                            )
                        }
                    }
                }
                item(key = "stored") {
                    Text(
                        text = "${dictionaries.size} on this device · ${formatBytes(storedBytes)}",
                        modifier = Modifier.padding(start = 8.dp, bottom = 12.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                itemsIndexed(dictionaries, key = { _, it -> it.id }) { index, dictionary ->
                    DictionaryRow(
                        index = index,
                        count = dictionaries.size,
                        dictionary = dictionary,
                        onEnabled = { onEnabled(dictionary, it) },
                        onMove = { onMove(dictionary, it) },
                        onDelete = { onDelete(dictionary) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DictionaryRow(
    index: Int,
    count: Int,
    dictionary: DictionaryRecord,
    onEnabled: (Boolean) -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    val shapes = DictionaryShapes
    var menu by remember { mutableStateOf(false) }
    SegmentedListItem(
        checked = dictionary.enabled,
        onCheckedChange = onEnabled,
        shapes = ListItemDefaults.segmentedShapes(index, count),
                    colors = segmentedItemColors(),
        leadingContent = {
            ShapeBadge(
                polygon = shapes[index % shapes.size],
                containerColor = if (dictionary.enabled) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                size = 52.dp,
            ) {
                Text(
                    text = (index + 1).toString(),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = if (dictionary.enabled) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        },
        supportingContent = {
            Text(text = dictionary.summaryLine(), maxLines = 2)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = dictionary.enabled, onCheckedChange = null)
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "More for ${dictionary.title}")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Move up") },
                            enabled = index > 0,
                            leadingIcon = { Icon(Icons.Rounded.ArrowUpward, contentDescription = null) },
                            onClick = { menu = false; onMove(-1) },
                        )
                        DropdownMenuItem(
                            text = { Text("Move down") },
                            enabled = index < count - 1,
                            leadingIcon = { Icon(Icons.Rounded.ArrowDownward, contentDescription = null) },
                            onClick = { menu = false; onMove(1) },
                        )
                        DropdownMenuItem(
                            text = { Text("Remove") },
                            leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }
        },
        content = { Text(dictionary.title, maxLines = 1) },
    )
}

/** The kind, revision, and entry count a dictionary actually contributes. */
private fun DictionaryRecord.summaryLine(): String = buildString {
    val kind = DictionaryCatalog.kindOf(this@summaryLine)
    kind?.let { append(it.label).append(" · ") }
    append(revision.ifBlank { "unknown revision" })
    when (kind) {
        DictionaryKind.KANJI -> counts.positive("kanji")?.let { append(" · %,d kanji".format(it)) }
        DictionaryKind.FREQUENCY -> (counts.positive("termMeta") ?: counts.positive("kanjiMeta"))
            ?.let { append(" · %,d entries".format(it)) }
        DictionaryKind.PITCH -> counts.positive("termMeta")?.let { append(" · %,d pitches".format(it)) }
        else -> {
            counts.positive("terms")?.let { append(" · %,d terms".format(it)) }
            counts.positive("kanji")?.let { append(" · %,d kanji".format(it)) }
        }
    }
}

private fun Map<String, Int>.positive(key: String): Int? = this[key]?.takeIf { it > 0 }
