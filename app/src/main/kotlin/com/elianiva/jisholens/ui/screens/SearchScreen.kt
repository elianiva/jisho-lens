package com.elianiva.jisholens.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import com.elianiva.jisholens.ui.components.EntryCard

@OptIn(ExperimentalMaterial3Api::class)
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
    val dictionaries by container.dictionaries.collectAsStateWithLifecycle()
    val hasDictionary = dictionaries.any { it.enabled }

    val content: @Composable (Modifier) -> Unit = { contentModifier ->
        Column(
            modifier = contentModifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            TextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(32.dp),
                placeholder = { Text("Search for kanji, words, etc") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = if (state.query.isNotEmpty()) {
                    {
                        IconButton(onClick = viewModel::clear) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                        }
                    }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.submit() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )

            if (state.results.isNotEmpty()) {
                Text(
                    text = "${state.results.size} entries",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when {
                    state.isLoading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text(
                            text = "Searching for \"${state.query}\"...",
                            modifier = Modifier.padding(top = 12.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    state.error != null -> Text(
                        text = state.error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    !hasDictionary -> EmptySearchState(
                        title = "No dictionary yet",
                        detail = "Import a Yomitan dictionary to start looking up words.",
                        action = onOpenDictionaries?.let { open ->
                            { TextButton(onClick = open) { Text("Import dictionary") } }
                        },
                    )
                    state.hasSearched && state.results.isEmpty() -> EmptySearchState(
                        title = "No results",
                        detail = "Try the full-text search setting for partial kana, or check the spelling.",
                        action = null,
                    )
                    !state.hasSearched -> EmptySearchState(
                        title = "Search a word",
                        detail = "Japanese, kanji, kana, or an English gloss.",
                        action = null,
                    )
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(
                            items = state.results,
                            key = { "${it.termId}:${it.dictionaryId}" },
                        ) { entry ->
                            EntryCard(
                                entry = entry,
                                repository = container.repository,
                                onQuery = viewModel::searchLink,
                            )
                        }
                    }
                }
            }
        }
    }

    if (onClose == null) {
        content(modifier)
    } else {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text("Look up") },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Outlined.Close, contentDescription = "Close")
                        }
                    },
                )
            },
        ) { padding ->
            content(Modifier.padding(padding))
        }
    }
}

@Composable
private fun EmptySearchState(
    title: String,
    detail: String,
    action: (@Composable () -> Unit)?,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(24.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = detail,
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        action?.let {
            Box(Modifier.padding(top = 8.dp)) { it() }
        }
    }
}
