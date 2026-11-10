package com.elianiva.jisholens.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elianiva.jisholens.data.AppContainer
import com.elianiva.jisholens.domain.Entry
import com.elianiva.jisholens.domain.ScanWord
import com.elianiva.jisholens.ui.components.CompactEntryCard
import com.elianiva.jisholens.ui.components.EntryCard
import com.elianiva.jisholens.ui.components.fitOrigin
import com.elianiva.jisholens.ui.components.fitScale
import com.elianiva.jisholens.ui.theme.JapaneseLocale

/**
 * The frozen reader over one captured frame. [generation] identifies the frame, so a
 * rescan reloads OCR with a fresh view model instead of showing the previous page.
 */
@Composable
fun ScreenScanScreen(
    container: AppContainer,
    generation: Long,
    message: ScreenScanMessage?,
    onRescan: () -> Unit,
    onSearchLine: (String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: ScreenScanViewModel = viewModel(
        key = "screen-scan-$generation",
        factory = viewModelFactory {
            initializer {
                ScreenScanViewModel(
                    repository = container.repository,
                    ocr = container.ocr,
                    frame = container.screenFrames.frame,
                    initialMessage = message,
                )
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    ScreenScanContent(
        state = state,
        loadMedia = container.repository::media,
        onTap = viewModel::onTap,
        onSelectCandidate = viewModel::selectCandidate,
        onToggleReader = viewModel::toggleReader,
        onSelectReaderWord = viewModel::selectReaderWord,
        onClearSelection = viewModel::clearSelection,
        onExpand = viewModel::expand,
        onCollapse = viewModel::collapse,
        onSearchLine = { onSearchLine(state.line?.text.orEmpty()) },
        onCopy = { text ->
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard?.setPrimaryClip(ClipData.newPlainText("Jisho Lens", text))
        },
        onRescan = onRescan,
        onClose = onClose,
    )
}

/** The frozen page with its recognised lines, and a card for the tapped word. */
@Composable
fun ScreenScanContent(
    state: ScreenScanUiState,
    loadMedia: suspend (Long, String) -> ByteArray?,
    onTap: (imageX: Float, imageY: Float) -> Unit,
    onSelectCandidate: (Int) -> Unit,
    onToggleReader: () -> Unit,
    onSelectReaderWord: (lineIndex: Int, word: ScanWord) -> Unit,
    onClearSelection: () -> Unit,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onSearchLine: () -> Unit,
    onCopy: (String) -> Unit,
    onRescan: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(Color.Black)) {
        if (!state.reader) {
            FrameCanvas(state = state, onTap = onTap)
        }

        if (state.reader) {
            ReaderPage(
                state = state,
                onSelectWord = onSelectReaderWord,
                modifier = Modifier.fillMaxSize(),
            )
        }

        ScreenTopBar(
            reader = state.reader,
            onToggleReader = onToggleReader,
            onRescan = onRescan,
            onClose = onClose,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        if (!state.expanded) {
            val card: @Composable () -> Unit = {
                when {
                    state.message != null -> MessageCard(
                        message = state.message,
                        onRescan = onRescan,
                        onClose = onClose,
                    )
                    state.loading -> LoadingCard()
                    state.candidates.isNotEmpty() -> WordCard(
                        state = state,
                        onSelectCandidate = onSelectCandidate,
                        onExpand = onExpand,
                        onClearSelection = onClearSelection,
                        onCopy = onCopy,
                    )
                    state.selectedLine >= 0 -> NoEntryCard(
                        surface = state.line?.text.orEmpty(),
                        onSearchLine = onSearchLine,
                        onCopy = onCopy,
                        onClearSelection = onClearSelection,
                    )
                    !state.reader -> HintCard()
                }
            }
            if (state.reader) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                ) {
                    card()
                }
            } else {
                Box(
                    modifier = Modifier
                        .align(if (state.cardAtTop) Alignment.TopCenter else Alignment.BottomCenter)
                        .fillMaxWidth()
                        .then(if (state.cardAtTop) Modifier.statusBarsPadding().padding(top = 64.dp, start = 16.dp, end = 16.dp) else Modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)),
                ) {
                    card()
                }
            }
        }

        if (state.expanded) {
            ExpandedEntry(
                entries = state.entries,
                loadMedia = loadMedia,
                onClose = onCollapse,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * The frame as captured: dimmed, except the selected line, which stays at full
 * brightness behind an outline so the word is never lost in the dim.
 */
@Composable
private fun FrameCanvas(
    state: ScreenScanUiState,
    onTap: (Float, Float) -> Unit,
) {
    var canvas by remember { mutableStateOf(IntSize.Zero) }
    val accent = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { canvas = it }
            .pointerInput(state.frame) {
                detectTapGestures { tap ->
                    val bitmap = state.frame ?: return@detectTapGestures
                    val base = fitScale(canvas, bitmap.width, bitmap.height)
                    val origin = fitOrigin(canvas, bitmap.width, bitmap.height, base)
                    onTap((tap.x - origin.x) / base, (tap.y - origin.y) / base)
                }
            },
    ) {
        val bitmap = state.frame ?: return@Canvas
        val base = fitScale(canvas, bitmap.width, bitmap.height)
        val origin = fitOrigin(canvas, bitmap.width, bitmap.height, base)
        val image = bitmap.asImageBitmap()
        withTransform({
            translate(origin.x, origin.y)
            scale(scaleX = base, scaleY = base, pivot = Offset.Zero)
        }) {
            drawImage(image, topLeft = Offset.Zero)
            drawRect(
                color = Color.Black.copy(alpha = 0.38f),
                size = Size(bitmap.width.toFloat(), bitmap.height.toFloat()),
            )
            state.lines.forEachIndexed { index, line ->
                if (index != state.selectedLine) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.06f),
                        topLeft = Offset(line.rect.left.toFloat() - 3f, line.rect.top.toFloat() - 3f),
                        size = Size(line.rect.width().toFloat() + 6f, line.rect.height().toFloat() + 6f),
                        cornerRadius = CornerRadius(8f),
                    )
                }
            }
            state.line?.let { line ->
                val left = line.rect.left.toFloat() - 4f
                val top = line.rect.top.toFloat() - 4f
                val width = line.rect.width().toFloat() + 8f
                val height = line.rect.height().toFloat() + 8f
                clipRect(left, top, left + width, top + height) {
                    drawImage(image, topLeft = Offset.Zero)
                    drawRoundRect(
                        color = accent.copy(alpha = 0.12f),
                        topLeft = Offset(left, top),
                        size = Size(width, height),
                        cornerRadius = CornerRadius(10f),
                    )
                }
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                    cornerRadius = CornerRadius(10f),
                    style = Stroke(width = 2f),
                )
            }
        }
    }
}

@Composable
private fun ScreenTopBar(
    reader: Boolean,
    onToggleReader: () -> Unit,
    onRescan: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Over the frame the bar sits on arbitrary content, so it needs its own dark
    // chips; over the reader's surface it should follow the theme.
    val colors = if (reader) {
        IconButtonDefaults.filledTonalIconButtonColors()
    } else {
        IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = Color.Black.copy(alpha = 0.55f),
            contentColor = Color.White,
        )
    }
    Row(
        modifier = modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledTonalIconButton(onClick = onClose, colors = colors) {
            Icon(Icons.Rounded.Close, contentDescription = "Close the reader")
        }
        Text(
            text = if (reader) "Reader" else "Frozen screen",
            modifier = Modifier.padding(start = 4.dp),
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = if (reader) MaterialTheme.colorScheme.onSurface else Color.White,
        )
        Spacer(Modifier.weight(1f))
        FilledTonalIconButton(onClick = onToggleReader, colors = colors) {
            Icon(
                imageVector = if (reader) Icons.AutoMirrored.Rounded.ArrowBack else Icons.AutoMirrored.Rounded.MenuBook,
                contentDescription = if (reader) "Back to the page" else "Read the whole page",
            )
        }
        FilledTonalIconButton(onClick = onRescan, colors = colors) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Rescan the screen")
        }
    }
}

/** The tapped word, its alternatives, and the entry in compact form. */
@Composable
private fun WordCard(
    state: ScreenScanUiState,
    onSelectCandidate: (Int) -> Unit,
    onExpand: () -> Unit,
    onClearSelection: () -> Unit,
    onCopy: (String) -> Unit,
) {
    val entry = state.entries.firstOrNull() ?: return
    val word = state.word
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.candidates.size > 1) {
            // The chips sit on their own surface: over a frozen page, text on the frame
            // has no contrast to rely on.
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 3.dp,
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FlowRow(
                        modifier = Modifier.weight(1f, fill = false),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        state.candidates.forEachIndexed { index, candidate ->
                            FilterChip(
                                selected = index == state.selectedCandidate,
                                onClick = { onSelectCandidate(index) },
                                label = {
                                    Text(
                                        text = candidate.surface,
                                        style = MaterialTheme.typography.titleSmall.copy(localeList = JapaneseLocale),
                                    )
                                },
                            )
                        }
                    }
                    Text(
                        text = "${state.selectedCandidate + 1} of ${state.candidates.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        CompactEntryCard(
            entry = entry,
            onExpand = onExpand,
            actions = {
                IconButton(onClick = { onCopy(entry.expression) }) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy the word")
                }
                IconButton(onClick = onClearSelection) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear the selection")
                }
            },
        )
        if (word != null && state.entries.size > 1) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Text(
                    text = "${state.entries.size} entries for ${word.surface}",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NoEntryCard(
    surface: String,
    onSearchLine: () -> Unit,
    onCopy: (String) -> Unit,
    onClearSelection: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = if (surface.isBlank()) "No entry" else "No entry for $surface",
                style = MaterialTheme.typography.titleMediumEmphasized.copy(localeList = JapaneseLocale),
            )
            Text(
                text = "The dictionary has no word at this position. Search the whole line, or copy it.",
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onSearchLine) { Text("Search the line") }
                TextButton(onClick = { onCopy(surface) }) { Text("Copy") }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onClearSelection) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear the selection")
                }
            }
        }
    }
}

@Composable
private fun MessageCard(
    message: ScreenScanMessage,
    onRescan: () -> Unit,
    onClose: () -> Unit,
) {
    val title: String
    val body: String
    when (message) {
        ScreenScanMessage.ConsentDenied -> {
            title = "Screen reading needs your permission"
            body = "Select text in the app you are reading and use Look up instead. That path needs no permission."
        }
        ScreenScanMessage.NoDictionary -> {
            title = "Set up a dictionary first"
            body = "Import a Yomitan dictionary, such as Jitendex, then try the tile again."
        }
        ScreenScanMessage.NoText -> {
            title = "No Japanese text found"
            body = "Try a sharper frame, a larger font, or another app."
        }
        ScreenScanMessage.Protected -> {
            title = "This app hides its screen"
            body = "A protected window captures as a blank frame, so there is nothing to read. Copy the text and look it up instead."
        }
        ScreenScanMessage.NoFrame -> {
            title = "The screen was not captured"
            body = "Nothing came back from the screen. Try the tile again, and make sure screen reading is still allowed."
        }
    }
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleLargeEmphasized)
            Text(
                text = body,
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onClose) { Text("Close") }
                if (message == ScreenScanMessage.NoText) {
                    TextButton(onClick = onRescan) { Text("Rescan") }
                }
            }
        }
    }
}

@Composable
private fun LoadingCard() {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ContainedLoadingIndicator(modifier = Modifier.size(28.dp))
            Text("Reading the screen…", style = MaterialTheme.typography.titleSmallEmphasized)
        }
    }
}

@Composable
private fun HintCard() {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Text(
            text = "Tap a word on the frozen page.",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            style = MaterialTheme.typography.titleSmallEmphasized,
        )
    }
}

/** Every recognised line, segmented, so any word on the page is one tap away. */
@Composable
private fun ReaderPage(
    state: ScreenScanUiState,
    onSelectWord: (Int, ScanWord) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier, color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 64.dp)) {
            if (state.readingPage && state.readerLines.isEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ContainedLoadingIndicator(modifier = Modifier.size(28.dp))
                    Text("Reading the page…", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    itemsIndexed(state.readerLines) { index, line ->
                        ReaderLineText(
                            line = line,
                            onSelectWord = { word -> onSelectWord(index, word) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderLineText(line: ReaderLine, onSelectWord: (ScanWord) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        var cursor = 0
        for (word in line.words) {
            if (word.start > cursor) {
                Text(
                    text = line.text.substring(cursor.coerceAtLeast(0), word.start),
                    style = MaterialTheme.typography.bodyLarge.copy(localeList = JapaneseLocale),
                )
            }
            if (word.entries.isEmpty()) {
                Text(
                    text = word.surface,
                    style = MaterialTheme.typography.bodyLarge.copy(localeList = JapaneseLocale),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = word.surface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onSelectWord(word) }
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                        .padding(horizontal = 3.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        localeList = JapaneseLocale,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
            cursor = word.end
        }
        if (cursor < line.text.length) {
            Text(
                text = line.text.substring(cursor.coerceAtLeast(0)),
                style = MaterialTheme.typography.bodyLarge.copy(localeList = JapaneseLocale),
            )
        }
    }
}

/** The full entry, every gloss and tag, replacing the frozen page. */
@Composable
private fun ExpandedEntry(
    entries: List<Entry>,
    loadMedia: suspend (Long, String) -> ByteArray?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier, color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to the page")
                }
                Text("Entry", style = MaterialTheme.typography.titleLargeEmphasized)
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(entries, key = { "${it.termId}:${it.dictionaryId}" }) { entry ->
                    EntryCard(entry = entry, loadMedia = loadMedia, onQuery = {})
                }
            }
        }
    }
}
