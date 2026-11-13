package com.elianiva.jisholens.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elianiva.jisholens.data.AppContainer
import com.elianiva.jisholens.ui.components.EntryCard
import com.elianiva.jisholens.ui.components.fitOrigin
import com.elianiva.jisholens.ui.components.fitScale
import com.elianiva.jisholens.ui.components.rememberTextSpeaker
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    container: AppContainer,
    imagePath: String,
    onBack: () -> Unit,
    onOpenKanji: ((Char) -> Unit)? = null,
) {
    val context = LocalContext.current
    val viewModel: ScanViewModel = viewModel(
        key = imagePath,
        factory = viewModelFactory {
            initializer {
                ScanViewModel(context.applicationContext, container.repository, container.notes, container.ocr, imagePath)
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notes by viewModel.savedNotes.collectAsStateWithLifecycle()
    val speaker = rememberTextSpeaker()
    ScanContent(
        state = state,
        loadMedia = container.repository::media,
        onTap = viewModel::onTap,
        onSelectWord = viewModel::selectWord,
        onQuery = viewModel::searchLink,
        onBack = onBack,
        notes = notes,
        onSaveNote = viewModel::saveNote,
        onOpenKanji = onOpenKanji,
        onSpeak = speaker?.let { voice -> { word: String -> voice.speak(word) } },
    )
}

/** The picture with its recognised lines, and a sheet of words and entries that rides over it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanContent(
    state: ScanUiState,
    loadMedia: suspend (Long, String) -> ByteArray?,
    onTap: (imageX: Float, imageY: Float) -> Unit,
    onSelectWord: (Int) -> Unit,
    onQuery: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    notes: Map<String, String> = emptyMap(),
    onSaveNote: ((String, String) -> Unit)? = null,
    onOpenKanji: ((Char) -> Unit)? = null,
    onSpeak: ((String) -> Unit)? = null,
) {
    val scaffoldState = rememberBottomSheetScaffoldState()
    val scope = rememberCoroutineScope()
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val currentZoom by rememberUpdatedState(zoom)
    val currentPan by rememberUpdatedState(pan)

    LaunchedEffect(state.bitmap) {
        zoom = 1f
        pan = Offset.Zero
    }
    LaunchedEffect(state.entries) {
        if (state.entries.isNotEmpty()) scaffoldState.bottomSheetState.expand()
    }

    BackHandler {
        if (scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded) {
            scope.launch { scaffoldState.bottomSheetState.partialExpand() }
        } else {
            onBack()
        }
    }

    BottomSheetScaffold(
        modifier = modifier,
        scaffoldState = scaffoldState,
        sheetPeekHeight = 132.dp,
        containerColor = Color.Black,
        sheetContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        sheetShape = MaterialTheme.shapes.extraExtraLarge.copy(bottomStart = androidx.compose.foundation.shape.ZeroCornerSize, bottomEnd = androidx.compose.foundation.shape.ZeroCornerSize),
        sheetContent = {
            ScanSheet(
                state = state,
                loadMedia = loadMedia,
                onSelectWord = onSelectWord,
                onQuery = onQuery,
                notes = notes,
                onSaveNote = onSaveNote,
                onOpenKanji = onOpenKanji,
                onSpeak = onSpeak,
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).background(Color.Black),
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(state.bitmap) {
                        detectTransformGestures { _, panChange, zoomChange, _ ->
                            zoom = (zoom * zoomChange).coerceIn(1f, 5f)
                            pan += panChange
                        }
                    }
                    .pointerInput(state.bitmap, canvasSize) {
                        detectTapGestures { tap ->
                            val bitmap = state.bitmap ?: return@detectTapGestures
                            val base = fitScale(canvasSize, bitmap.width, bitmap.height)
                            val origin = fitOrigin(canvasSize, bitmap.width, bitmap.height, base)
                            val total = base * currentZoom
                            onTap(
                                (tap.x - origin.x - currentPan.x) / total,
                                (tap.y - origin.y - currentPan.y) / total,
                            )
                        }
                    },
            ) {
                val bitmap = state.bitmap ?: return@Canvas
                val base = fitScale(canvasSize, bitmap.width, bitmap.height)
                val origin = fitOrigin(canvasSize, bitmap.width, bitmap.height, base)
                val total = base * zoom
                withTransform({
                    translate(origin.x + pan.x, origin.y + pan.y)
                    scale(scaleX = total, scaleY = total, pivot = Offset.Zero)
                }) {
                    drawImage(bitmap.asImageBitmap(), topLeft = Offset.Zero)
                    drawRect(
                        color = Color.Black.copy(alpha = 0.4f),
                        size = Size(bitmap.width.toFloat(), bitmap.height.toFloat()),
                    )
                    state.lines.forEachIndexed { index, line ->
                        val selected = index == state.selectedLine
                        drawRoundRect(
                            color = if (selected) Color(0xFF8C9EFF).copy(alpha = 0.45f) else Color.White.copy(alpha = 0.22f),
                            topLeft = Offset(line.rect.left - 4f, line.rect.top - 4f),
                            size = Size(line.rect.width().toFloat() + 8f, line.rect.height().toFloat() + 8f),
                            cornerRadius = CornerRadius(8f),
                        )
                    }
                }
            }

            if (state.loading) {
                ContainedLoadingIndicator(modifier = Modifier.align(Alignment.Center).size(72.dp))
            }
            state.error?.let { error ->
                Text(
                    text = error,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = Color.White,
                )
            }
            if (!state.loading && state.error == null && state.lines.isEmpty()) {
                Text(
                    text = "No Japanese text was recognised.\nTry a different image.",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }

            FilledTonalIconButton(
                onClick = onBack,
                modifier = Modifier.statusBarsPadding().padding(12.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.55f),
                    contentColor = Color.White,
                ),
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
        }
    }
}

@Composable
private fun ScanSheet(
    state: ScanUiState,
    loadMedia: suspend (Long, String) -> ByteArray?,
    onSelectWord: (Int) -> Unit,
    onQuery: (String) -> Unit,
    notes: Map<String, String>,
    onSaveNote: ((String, String) -> Unit)?,
    onOpenKanji: ((Char) -> Unit)?,
    onSpeak: ((String) -> Unit)?,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (state.words.isEmpty()) {
            Text(
                "Tap a line",
                modifier = Modifier.padding(vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(state.words) { index, word ->
                    FilterChip(
                        selected = index == state.selectedWord,
                        onClick = { onSelectWord(index) },
                        label = { Text(word.surface, style = MaterialTheme.typography.titleMedium.copy(localeList = com.elianiva.jisholens.ui.theme.JapaneseLocale)) },
                        shape = androidx.compose.foundation.shape.CircleShape,
                    )
                }
            }
        }

        if (state.entries.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                contentPadding = PaddingValues(vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.entries, key = { "${it.termId}:${it.dictionaryId}" }) { entry ->
                    EntryCard(
                        entry = entry,
                        loadMedia = loadMedia,
                        onQuery = onQuery,
                        onKanji = onOpenKanji,
                        note = notes[entry.expression].orEmpty(),
                        onSaveNote = onSaveNote?.let { save -> { value -> save(entry.expression, value) } },
                        onSpeak = onSpeak?.let { speak -> { speak(entry.expression) } },
                    )
                }
            }
        } else if (state.words.isNotEmpty() && state.selectedWord >= 0) {
            Text(
                "No entry found for this word.",
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}


