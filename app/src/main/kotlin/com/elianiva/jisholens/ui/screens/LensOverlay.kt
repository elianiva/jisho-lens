package com.elianiva.jisholens.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.elianiva.jisholens.dictionary.Furigana
import com.elianiva.jisholens.domain.Coverage
import com.elianiva.jisholens.domain.Entry
import com.elianiva.jisholens.domain.ScanWord
import com.elianiva.jisholens.domain.hasKanji
import com.elianiva.jisholens.domain.isKnown
import com.elianiva.jisholens.screen.LensMessage
import com.elianiva.jisholens.screen.LensUiState
import com.elianiva.jisholens.screen.wordRect
import com.elianiva.jisholens.ui.components.CandidateSheet
import com.elianiva.jisholens.ui.components.ExpandedEntrySheet
import com.elianiva.jisholens.ui.components.fitOrigin
import com.elianiva.jisholens.ui.components.fitScale
import com.elianiva.jisholens.ui.theme.JapaneseLocale
import kotlin.math.abs

/**
 * The live lens over another app.
 *
 * The page stays where it is: the overlay is transparent and only draws an underline
 * under Japanese lines plus the selected word's box. Tapping a word slides its entry
 * up from the bottom; the page is not replaced by a screenshot at any point.
 *
 * [screenSize] is the display the accessibility bounds are in. It normally equals the
 * window, and the fit transform only matters in previews and on a mismatched surface.
 */
@Composable
fun LensOverlayContent(
    state: LensUiState,
    screenSize: IntSize,
    loadMedia: suspend (Long, String) -> ByteArray?,
    onTap: (imageX: Float, imageY: Float) -> Unit,
    onSelectCandidate: (Int) -> Unit,
    onToggleKnown: () -> Unit,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onClearSelection: () -> Unit,
    onRescan: () -> Unit,
    onReadImage: () -> Unit,
    onScroll: (forward: Boolean) -> Unit,
    onCopy: (String) -> Unit,
    onMine: (Entry) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onSaveNote: ((String, String) -> Unit)? = null,
    onSpeak: ((String) -> Unit)? = null,
    onToggleStudy: () -> Unit = {},
) {
    if (state.expanded) {
        ExpandedEntrySheet(
            entries = state.entries,
            loadMedia = loadMedia,
            onBack = onCollapse,
            noteFor = { entry -> state.notes[entry.expression].orEmpty() },
            onSaveNote = onSaveNote,
            onSpeak = onSpeak,
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    var sheetTop by remember { mutableFloatStateOf(Float.POSITIVE_INFINITY) }
    var topBarBottom by remember { mutableFloatStateOf(0f) }

    Box(modifier.fillMaxSize()) {
        HighlightLayer(
            state = state,
            screenSize = screenSize,
            onTap = { x, y ->
                if (y < sheetTop && y > topBarBottom) onTap(x, y)
            },
            onScroll = onScroll,
            sheetTop = sheetTop,
            topBarBottom = topBarBottom,
        )
        LensTopBar(
            canReadImage = state.canReadImage,
            busy = state.scanning,
            study = state.study,
            onToggleStudy = onToggleStudy,
            onReadImage = onReadImage,
            onRescan = onRescan,
            onClose = onClose,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .onGloballyPositioned { topBarBottom = it.positionInWindow().y + it.size.height },
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .onGloballyPositioned { sheetTop = it.positionInWindow().y },
        ) {
            val kind = when {
                state.message != null -> SheetKind.Message
                state.scanning -> SheetKind.Loading
                state.lookingUp -> SheetKind.Lookup
                state.candidates.isNotEmpty() -> SheetKind.Word
                state.selectedLine >= 0 -> SheetKind.NoEntry
                else -> SheetKind.Hint
            }
            AnimatedContent(
                targetState = kind,
                transitionSpec = {
                    (slideInVertically(initialOffsetY = { it / 4 }) + fadeIn()) togetherWith fadeOut()
                },
                label = "lens-sheet",
            ) { target ->
                when (target) {
                    SheetKind.Message -> MessageCard(
                        message = state.message ?: LensMessage.NoText,
                        canReadImage = state.canReadImage,
                        onReadImage = onReadImage,
                        onRescan = onRescan,
                        onClose = onClose,
                    )
                    SheetKind.Loading -> LoadingCard("Reading the screen…")
                    SheetKind.Lookup -> LoadingCard("Looking up…")
                    SheetKind.Word -> CandidateSheet(
                        candidates = state.candidates,
                        selectedCandidate = state.selectedCandidate,
                        knownWords = state.knownWords,
                        note = state.entries.firstOrNull()?.let { entry -> state.notes[entry.expression] }.orEmpty(),
                        onSelectCandidate = onSelectCandidate,
                        onToggleKnown = onToggleKnown,
                        onExpand = onExpand,
                        onClearSelection = onClearSelection,
                        onCopy = onCopy,
                        onMine = onMine,
                        onSpeak = onSpeak,
                        onSpeakSentence = state.line?.text
                            ?.takeIf { onSpeak != null }
                            ?.let { sentence -> { onSpeak?.invoke(sentence) } },
                        onSaveNote = onSaveNote,
                        coverage = if (state.study) state.coverage else null,
                    )
                    SheetKind.NoEntry -> NoEntryCard(
                        surface = state.line?.text.orEmpty(),
                        onCopy = onCopy,
                        onClearSelection = onClearSelection,
                    )
                    SheetKind.Hint -> HintPill(coverage = if (state.study) state.coverage else null)
                }
            }
        }
    }
}

/** The transparent highlight plane: one thin underline per Japanese line, a box for the selection. */
@Composable
private fun HighlightLayer(
    state: LensUiState,
    screenSize: IntSize,
    onTap: (Float, Float) -> Unit,
    onScroll: (Boolean) -> Unit,
    sheetTop: Float,
    topBarBottom: Float,
) {
    val accent = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val underline = with(density) { 2.dp.toPx() }
    val shadow = with(density) { 5.dp.toPx() }
    val textMeasurer = rememberTextMeasurer()
    var canvas by remember { mutableStateOf(IntSize.Zero) }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { canvas = it }
            .pointerInput(state.lines, screenSize, canvas, sheetTop, topBarBottom) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (down.position.y >= sheetTop || down.position.y <= topBarBottom) {
                        return@awaitEachGesture
                    }
                    var total = Offset.Zero
                    var change = down
                    while (change.pressed) {
                        val event = awaitPointerEvent()
                        val next = event.changes.firstOrNull { it.id == down.id } ?: break
                        total += next.positionChange()
                        if (total.getDistance() > slop) next.consume()
                        change = next
                    }
                    if (total.getDistance() <= slop) {
                        val base = fitScale(canvas, screenSize.width, screenSize.height)
                        val origin = fitOrigin(canvas, screenSize.width, screenSize.height, base)
                        val x = (down.position.x - origin.x) / base
                        val y = (down.position.y - origin.y) / base
                        if (x in 0f..screenSize.width.toFloat() && y in 0f..screenSize.height.toFloat()) {
                            onTap(x, y)
                        }
                    } else if (abs(total.y) > abs(total.x)) {
                        onScroll(total.y < 0f)
                    }
                }
            },
    ) {
        if (canvas.width == 0 || canvas.height == 0 || screenSize.width == 0 || screenSize.height == 0) return@Canvas
        val base = fitScale(canvas, screenSize.width, screenSize.height)
        val origin = fitOrigin(canvas, screenSize.width, screenSize.height, base)
        withTransform({
            translate(origin.x, origin.y)
            scale(scaleX = base, scaleY = base, pivot = Offset.Zero)
        }) {
            state.lines.forEachIndexed { index, line ->
                val left = line.rect.left.toFloat()
                val right = line.rect.right.toFloat()
                val bottom = line.rect.bottom.toFloat()
                if (right <= left) return@forEachIndexed
                if (index == state.selectedLine) {
                    drawRoundRect(
                        color = accent.copy(alpha = 0.10f),
                        topLeft = Offset(left - 4f, line.rect.top - 4f),
                        size = Size(line.rect.width() + 8f, line.rect.height() + 8f),
                        cornerRadius = CornerRadius(10f),
                    )
                    drawRoundRect(
                        color = accent,
                        topLeft = Offset(left - 4f, line.rect.top - 4f),
                        size = Size(line.rect.width() + 8f, line.rect.height() + 8f),
                        cornerRadius = CornerRadius(10f),
                        style = Stroke(width = underline),
                    )
                } else {
                    drawLine(
                        color = Color.Black.copy(alpha = 0.35f),
                        start = Offset(left, bottom),
                        end = Offset(right, bottom),
                        strokeWidth = shadow,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = accent,
                        start = Offset(left, bottom),
                        end = Offset(right, bottom),
                        strokeWidth = underline,
                        cap = StrokeCap.Round,
                    )
                }
            }
            state.wordBox?.let { box ->
                drawRoundRect(
                    color = accent.copy(alpha = 0.28f),
                    topLeft = Offset(box.left - 3f, box.top - 3f),
                    size = Size(box.width() + 6f, box.height() + 6f),
                    cornerRadius = CornerRadius(8f),
                )
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(box.left - 3f, box.top - 3f),
                    size = Size(box.width() + 6f, box.height() + 6f),
                    cornerRadius = CornerRadius(8f),
                    style = Stroke(width = underline),
                )
            }
            if (state.study) {
                state.lineWords.forEach { (index, words) ->
                    val line = state.lines.getOrNull(index) ?: return@forEach
                    words.forEach { word ->
                        val rect = line.wordRect(word.start, word.end)
                        if (rect.width() <= 0) return@forEach
                        val known = word.isKnown(state.knownWords)
                        if (!known) {
                            drawRoundRect(
                                color = accent.copy(alpha = 0.16f),
                                topLeft = Offset(rect.left - 2f, rect.top - 2f),
                                size = Size(rect.width() + 4f, rect.height() + 4f),
                                cornerRadius = CornerRadius(6f),
                            )
                        }
                        val entry = word.entries.firstOrNull() ?: return@forEach
                        val reading = Furigana.surfaceReading(word.surface, entry.ruby).ifBlank { entry.reading }
                        if (reading.isBlank() || reading == word.surface || !word.surface.hasKanji()) return@forEach
                        val style = TextStyle(
                            color = if (known) accent.copy(alpha = 0.55f) else accent,
                            fontSize = with(density) {
                                (line.rect.height() * 0.30f).coerceIn(10f, 44f).toSp()
                            },
                            localeList = JapaneseLocale,
                        )
                        val layout = textMeasurer.measure(AnnotatedString(reading), style = style, maxLines = 1)
                        if (layout.size.width > rect.width() + 8f) return@forEach
                        drawText(
                            textLayoutResult = layout,
                            topLeft = Offset(
                                x = rect.left + (rect.width() - layout.size.width) / 2f,
                                y = (rect.top - layout.size.height - 1f).coerceAtLeast(0f),
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LensTopBar(
    canReadImage: Boolean,
    busy: Boolean,
    study: Boolean,
    onToggleStudy: () -> Unit,
    onReadImage: () -> Unit,
    onRescan: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = Color.Black.copy(alpha = 0.55f),
        contentColor = Color.White,
    )
    val studyColors = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = if (study) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.55f),
        contentColor = if (study) MaterialTheme.colorScheme.onPrimary else Color.White,
    )
    Row(
        modifier = modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledTonalIconButton(onClick = onClose, colors = colors) {
            Icon(Icons.Rounded.Close, contentDescription = "Close the lens")
        }
        Spacer(Modifier.weight(1f))
        FilledTonalIconButton(onClick = onToggleStudy, colors = studyColors) {
            Icon(
                imageVector = Icons.Rounded.Translate,
                contentDescription = if (study) "Hide furigana and word difficulty" else "Show furigana and word difficulty",
            )
        }
        if (canReadImage) {
            FilledTonalIconButton(onClick = onReadImage, enabled = !busy, colors = colors) {
                Icon(Icons.Rounded.DocumentScanner, contentDescription = "Read the image instead")
            }
        }
        FilledTonalIconButton(onClick = onRescan, enabled = !busy, colors = colors) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Read the screen again")
        }
    }
}

private enum class SheetKind { Loading, Lookup, Message, Word, NoEntry, Hint }

@Composable
private fun NoEntryCard(
    surface: String,
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
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMediumEmphasized.copy(localeList = JapaneseLocale),
            )
            Text(
                text = "The dictionary has no word at this position.",
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onCopy(surface) }) { Text("Copy the line") }
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
    message: LensMessage,
    canReadImage: Boolean,
    onReadImage: () -> Unit,
    onRescan: () -> Unit,
    onClose: () -> Unit,
) {
    val title: String
    val body: String
    when (message) {
        LensMessage.NoText -> {
            title = "No Japanese text here"
            body = if (canReadImage) {
                "This app exposes no text. Read the image instead, or try another app."
            } else {
                "This app exposes no text. Try another app, or update to Android 11 to read images."
            }
        }
        LensMessage.Protected -> {
            title = "This app hides its screen"
            body = "A protected window cannot be read. Copy the text and look it up instead."
        }
        LensMessage.OcrUnavailable -> {
            title = "Image reading is not available"
            body = "Reading an image needs Android 11 and the Japanese text model from Google Play. Text from the app still works."
        }
        LensMessage.Failed -> {
            title = "The screen was not read"
            body = "Try again, or copy the text and look it up."
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
                if (message == LensMessage.NoText && canReadImage) {
                    TextButton(onClick = onReadImage) { Text("Read the image") }
                }
                TextButton(onClick = onRescan) { Text("Rescan") }
                TextButton(onClick = onClose) { Text("Close") }
            }
        }
    }
}

@Composable
private fun LoadingCard(text: String) {
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
            Text(text, style = MaterialTheme.typography.titleSmallEmphasized)
        }
    }
}

@Composable
private fun HintPill(coverage: Coverage? = null) {
    val known = coverage?.takeIf { it.total > 0 }
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 3.dp,
        modifier = if (known != null) {
            Modifier.semantics {
                contentDescription = "Page coverage: ${known.percent} percent, ${known.known} of ${known.total} words known"
            }
        } else {
            Modifier
        },
    ) {
        Text(
            text = if (known != null) "${known.percent}% known · Tap a word" else "Tap a word",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            style = MaterialTheme.typography.titleSmallEmphasized,
        )
    }
}
