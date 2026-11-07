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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import kotlinx.coroutines.launch
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    container: AppContainer,
    imagePath: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: ScanViewModel = viewModel(
        key = imagePath,
        factory = viewModelFactory {
            initializer {
                ScanViewModel(context.applicationContext, container.repository, container.ocr, imagePath)
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
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
        scaffoldState = scaffoldState,
        sheetPeekHeight = 108.dp,
        containerColor = Color.Black,
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        sheetContent = {
            ScanSheet(
                state = state,
                container = container,
                onSelectWord = viewModel::selectWord,
                onQuery = viewModel::searchLink,
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
                            viewModel.onTap(
                                imageX = (tap.x - origin.x - currentPan.x) / total,
                                imageY = (tap.y - origin.y - currentPan.y) / total,
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
                            color = if (selected) Color(0xFF4C8DFF).copy(alpha = 0.30f) else Color.White.copy(alpha = 0.20f),
                            topLeft = Offset(line.rect.left - 4f, line.rect.top - 4f),
                            size = Size(line.rect.width().toFloat() + 8f, line.rect.height().toFloat() + 8f),
                            cornerRadius = CornerRadius(8f),
                        )
                    }
                }
            }

            if (state.loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
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
                    text = "No Japanese text was recognised.\nTry scanning a different image.",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text("Scan", color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun ScanSheet(
    state: ScanUiState,
    container: AppContainer,
    onSelectWord: (Int) -> Unit,
    onQuery: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (state.words.isEmpty()) {
            Text(
                if (state.lines.isEmpty()) "Tap a line in the image to look up a word."
                else "Tap a recognised line to see its words.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(state.words) { index, word ->
                    FilterChip(
                        selected = index == state.selectedWord,
                        onClick = { onSelectWord(index) },
                        label = { Text(word.surface) },
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
                        repository = container.repository,
                        onQuery = onQuery,
                    )
                }
            }
        } else if (state.words.isNotEmpty() && state.selectedWord >= 0) {
            Text(
                "No entry found for this word.",
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun fitScale(canvas: IntSize, width: Int, height: Int): Float {
    if (canvas.width == 0 || canvas.height == 0 || width == 0 || height == 0) return 1f
    return min(canvas.width.toFloat() / width, canvas.height.toFloat() / height)
}

private fun fitOrigin(canvas: IntSize, width: Int, height: Int, scale: Float): Offset {
    val drawnWidth = width * scale
    val drawnHeight = height * scale
    return Offset((canvas.width - drawnWidth) / 2f, (canvas.height - drawnHeight) / 2f)
}

