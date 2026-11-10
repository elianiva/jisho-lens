package com.elianiva.jisholens.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elianiva.jisholens.data.AppContainer
import com.elianiva.jisholens.ui.components.EmptyState
import com.elianiva.jisholens.ui.components.EntryCard
import com.elianiva.jisholens.ui.components.LargeActionButton
import com.elianiva.jisholens.ui.theme.JapaneseLocale

/** What the lookup screen can do with the dictionaries it has. */
enum class DictionaryStatus { None, AllOff, Ready }

@Composable
fun SearchScreen(
    container: AppContainer,
    onOpenDictionaries: (() -> Unit)?,
    modifier: Modifier = Modifier,
    initialQuery: String? = null,
    onClose: (() -> Unit)? = null,
) {
    val viewModel: SearchViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                SearchViewModel(
                    repository = container.repository,
                    settings = container.settings,
                    savedStateHandle = createSavedStateHandle(),
                    initialQuery = initialQuery,
                )
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recents by viewModel.recents.collectAsStateWithLifecycle()
    val dictionaries by container.dictionaries.collectAsStateWithLifecycle()
    val status = when {
        dictionaries.isEmpty() -> DictionaryStatus.None
        dictionaries.none { it.enabled } -> DictionaryStatus.AllOff
        else -> DictionaryStatus.Ready
    }

    SearchContent(
        state = state,
        recents = recents,
        status = status,
        loadMedia = container.repository::media,
        onQueryChange = viewModel::onQueryChange,
        onSubmit = viewModel::submit,
        onClear = viewModel::clear,
        onPickQuery = viewModel::searchLink,
        onClearRecents = viewModel::clearRecents,
        onOpenDictionaries = onOpenDictionaries,
        modifier = modifier,
        onClose = onClose,
    )
}

@Composable
fun SearchContent(
    state: SearchUiState,
    recents: List<String>,
    status: DictionaryStatus,
    loadMedia: suspend (Long, String) -> ByteArray?,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    onPickQuery: (String) -> Unit,
    onClearRecents: () -> Unit,
    onOpenDictionaries: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
) {
    val body: @Composable (Modifier) -> Unit = { bodyModifier ->
        Column(
            if (onClose == null) bodyModifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars) else bodyModifier.fillMaxSize(),
        ) {
            SearchField(
                query = state.query,
                onQueryChange = onQueryChange,
                onSubmit = onSubmit,
                onClear = onClear,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                SearchBody(
                    state = state,
                    recents = recents,
                    status = status,
                    loadMedia = loadMedia,
                    onPickQuery = onPickQuery,
                    onClearRecents = onClearRecents,
                    onOpenDictionaries = onOpenDictionaries,
                )
            }
        }
    }

    if (onClose == null) {
        body(modifier)
    } else {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text("Look up", style = MaterialTheme.typography.titleLargeEmphasized) },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close")
                        }
                    },
                )
            },
        ) { padding ->
            body(Modifier.padding(padding))
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Search bar margins: 24dp at rest, 12dp focused, so the field widens as you start typing.
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val margin by animateDpAsState(
        targetValue = if (focused) 12.dp else 24.dp,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "search-margin",
    )
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth().padding(horizontal = margin),
        interactionSource = interaction,
        singleLine = true,
        shape = CircleShape,
        textStyle = MaterialTheme.typography.bodyLarge.copy(localeList = JapaneseLocale),
        placeholder = { Text("Search a word, kanji or gloss", style = MaterialTheme.typography.bodyLarge) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = onClear) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                }
            }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = Color.Transparent,
        ),
    )
}

@Composable
private fun SearchBody(
    state: SearchUiState,
    recents: List<String>,
    status: DictionaryStatus,
    loadMedia: suspend (Long, String) -> ByteArray?,
    onPickQuery: (String) -> Unit,
    onClearRecents: () -> Unit,
    onOpenDictionaries: (() -> Unit)?,
) {
    when {
        status == DictionaryStatus.None -> EmptyState(
            polygon = MaterialShapes.Cookie9Sided,
            icon = Icons.Rounded.LibraryBooks,
            title = "Add a dictionary first",
            modifier = Modifier.padding(top = 32.dp),
            action = onOpenDictionaries?.let { open ->
                { LargeActionButton("Import dictionary", Icons.Rounded.LibraryBooks, onClick = open) }
            },
        )
        status == DictionaryStatus.AllOff -> EmptyState(
            polygon = MaterialShapes.Cookie9Sided,
            icon = Icons.Rounded.Translate,
            title = "No dictionary is on",
            modifier = Modifier.padding(top = 32.dp),
            action = onOpenDictionaries?.let { open ->
                { LargeActionButton("Manage dictionaries", Icons.Rounded.Translate, onClick = open, tonal = true) }
            },
        )
        state.error != null -> EmptyState(
            polygon = MaterialShapes.SoftBurst,
            icon = Icons.Rounded.SearchOff,
            title = "The search failed",
            body = state.error,
            modifier = Modifier.padding(top = 32.dp),
        )
        state.results.isNotEmpty() -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "count") {
                Text(
                    text = if (state.results.size == 1) "1 entry" else "${state.results.size} entries",
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(items = state.results, key = { "${it.termId}:${it.dictionaryId}" }) { entry ->
                EntryCard(entry = entry, loadMedia = loadMedia, onQuery = onPickQuery)
            }
        }
        state.isLoading -> {
            // Under 200 ms the answer is instant, so the indicator waits before it shows.
            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(200)
                visible = true
            }
            if (visible) {
                Box(Modifier.fillMaxSize().padding(top = 96.dp), contentAlignment = Alignment.TopCenter) {
                    LoadingIndicator(Modifier.padding(16.dp))
                }
            }
        }
        state.hasSearched -> EmptyState(
            polygon = MaterialShapes.Pentagon,
            icon = Icons.Rounded.SearchOff,
            title = "No matches",
            modifier = Modifier.padding(top = 32.dp),
        )
        recents.isNotEmpty() -> Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Recent",
                    modifier = Modifier.weight(1f).padding(start = 8.dp, top = 16.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    color = MaterialTheme.colorScheme.primary,
                )
                TextButton(onClick = onClearRecents) { Text("Clear") }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                recents.forEach { word ->
                    AssistChip(
                        onClick = { onPickQuery(word) },
                        label = { Text(word, style = MaterialTheme.typography.titleMedium.copy(localeList = JapaneseLocale)) },
                        leadingIcon = { Icon(Icons.Rounded.History, contentDescription = null) },
                        shape = CircleShape,
                    )
                }
            }
        }
        else -> EmptyState(
            polygon = MaterialShapes.Clover4Leaf,
            icon = Icons.Rounded.Search,
            title = "Look up a word",
            modifier = Modifier.padding(top = 32.dp),
        )
    }
}
