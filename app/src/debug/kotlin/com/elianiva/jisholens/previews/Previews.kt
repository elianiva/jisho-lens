package com.elianiva.jisholens.previews

import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.elianiva.jisholens.data.ComponentKind
import com.elianiva.jisholens.data.ComponentState
import com.elianiva.jisholens.data.ComponentStatus
import com.elianiva.jisholens.data.ImportState
import com.elianiva.jisholens.data.Settings
import com.elianiva.jisholens.dictionary.DictionaryRecord
import com.elianiva.jisholens.dictionary.ImportSummary
import com.elianiva.jisholens.ui.components.ComponentDownloadPill
import com.elianiva.jisholens.ui.components.EntryCard
import com.elianiva.jisholens.ui.components.WithoutDecorativeMotion
import com.elianiva.jisholens.ui.screens.DictionariesContent
import com.elianiva.jisholens.ui.screens.DictionaryStatus
import com.elianiva.jisholens.ui.screens.OnboardingContent
import com.elianiva.jisholens.domain.ScanWord
import com.elianiva.jisholens.screen.LensLine
import com.elianiva.jisholens.screen.LensMessage
import com.elianiva.jisholens.screen.LensUiState
import com.elianiva.jisholens.ui.components.fitOrigin
import com.elianiva.jisholens.ui.components.fitScale
import com.elianiva.jisholens.ui.screens.LensOverlayContent
import com.elianiva.jisholens.ui.screens.LensSetupScreen
import com.elianiva.jisholens.ui.screens.ScanContent
import com.elianiva.jisholens.ui.screens.ScanHomeContent
import com.elianiva.jisholens.ui.screens.ScanUiState
import com.elianiva.jisholens.ui.screens.SearchContent
import com.elianiva.jisholens.ui.screens.SearchUiState
import com.elianiva.jisholens.ui.screens.SettingsContent
import com.elianiva.jisholens.ui.theme.JishoLensTheme
import kotlin.math.roundToInt

private const val W = 411
private const val H = 891

private val noMedia: suspend (Long, String) -> ByteArray? = { _, _ -> null }

/** Static palette and still decoration, so previews and screenshots are repeatable. */
@Composable
private fun Frame(dark: Boolean, content: @Composable () -> Unit) {
    JishoLensTheme(darkTheme = dark, dynamicColor = false) {
        WithoutDecorativeMotion {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) { content() }
        }
    }
}

@Composable
private fun Search(
    state: SearchUiState,
    recents: List<String> = emptyList(),
    status: DictionaryStatus = DictionaryStatus.Ready,
) {
    SearchContent(
        state = state,
        recents = recents,
        status = status,
        loadMedia = noMedia,
        onQueryChange = {},
        onSubmit = {},
        onClear = {},
        onPickQuery = {},
        onClearRecents = {},
        onOpenDictionaries = {},
    )
}

private val results = SearchUiState(
    query = "食べる",
    results = listOf(Samples.taberu, Samples.miru),
    hasSearched = true,
)

@Preview(name = "Onboarding", widthDp = W, heightDp = H)
@Composable
fun OnboardingLight() = Frame(false) { OnboardingContent(onImport = {}, onSkip = {}) }

@Preview(name = "Onboarding dark", widthDp = W, heightDp = H)
@Composable
fun OnboardingDark() = Frame(true) { OnboardingContent(onImport = {}, onSkip = {}) }

@Preview(name = "Onboarding downloading", widthDp = W, heightDp = H)
@Composable
fun OnboardingDownloading() = Frame(false) {
    OnboardingContent(
        onImport = {},
        onSkip = {},
        importState = ImportState.Downloading("Jitendex (1 of 4)", 12L * 1024 * 1024, 38L * 1024 * 1024),
    )
}

@Preview(name = "Catalogue", widthDp = W, heightDp = 3400)
@Composable
fun Catalogue() = Dictionaries(false, ImportState.Idle, emptyList())

@Preview(name = "Search recents", widthDp = W, heightDp = H)
@Composable
fun SearchRecents() = Frame(false) {
    Search(SearchUiState(), recents = listOf("食べる", "日本語", "見る", "ありがとう", "お疲れ様"))
}

@Preview(name = "Search results", widthDp = W, heightDp = H)
@Composable
fun SearchResultsLight() = Frame(false) { Search(results) }

@Preview(name = "Search results dark", widthDp = W, heightDp = H)
@Composable
fun SearchResultsDark() = Frame(true) { Search(results) }

@Preview(name = "Search deinflected", widthDp = W, heightDp = H)
@Composable
fun SearchDeinflected() = Frame(false) {
    Search(SearchUiState(query = "食べさせられた", results = listOf(Samples.taberareta), hasSearched = true))
}

@Preview(name = "Search no matches", widthDp = W, heightDp = H)
@Composable
fun SearchNoMatches() = Frame(false) { Search(SearchUiState(query = "zzzz", hasSearched = true)) }

@Preview(name = "Search no dictionary", widthDp = W, heightDp = H)
@Composable
fun SearchNoDictionary() = Frame(false) { Search(SearchUiState(), status = DictionaryStatus.None) }

@Preview(name = "Entry card", widthDp = W, heightDp = 560)
@Composable
fun EntryCardLight() = Frame(false) {
    Box(Modifier.padding(16.dp)) { EntryCard(Samples.taberu, noMedia, onQuery = {}) }
}

@Preview(name = "Entry card dark", widthDp = W, heightDp = 560)
@Composable
fun EntryCardDark() = Frame(true) {
    Box(Modifier.padding(16.dp)) { EntryCard(Samples.taberu, noMedia, onQuery = {}) }
}

@Preview(name = "Scan home", widthDp = W, heightDp = H)
@Composable
fun ScanHomeReady() = Frame(false) {
    ScanHomeContent(hasDictionary = true, onOpenDictionaries = {}, onCamera = {}, onGallery = {})
}

@Preview(name = "Scan home no dictionary", widthDp = W, heightDp = H)
@Composable
fun ScanHomeNoDictionary() = Frame(false) {
    ScanHomeContent(hasDictionary = false, onOpenDictionaries = {}, onCamera = {}, onGallery = {})
}

@Composable
private fun Scan(withEntry: Boolean) = Frame(false) {
    val (bitmap, lines) = remember { Samples.scanPage() }
    ScanContent(
        state = ScanUiState(
            bitmap = bitmap,
            lines = lines,
            selectedLine = if (withEntry) 1 else -1,
            words = if (withEntry) Samples.scanWords else emptyList(),
            selectedWord = if (withEntry) 2 else -1,
            entries = if (withEntry) Samples.scanWords[2].entries else emptyList(),
            loading = false,
        ),
        loadMedia = noMedia,
        onTap = { _, _ -> },
        onSelectWord = {},
        onQuery = {},
        onBack = {},
    )
}

@Preview(name = "Scan", widthDp = W, heightDp = H)
@Composable
fun ScanIdle() = Scan(withEntry = false)

@Preview(name = "Scan with entry", widthDp = W, heightDp = H)
@Composable
fun ScanWithEntry() = Scan(withEntry = true)

@Composable
private fun Dictionaries(dark: Boolean, state: ImportState, list: List<DictionaryRecord>) = Frame(dark) {
    DictionariesContent(
        dictionaries = list,
        importState = state,
        storedBytes = 412L * 1024 * 1024,
        snackbar = remember { SnackbarHostState() },
        onBack = {},
        onImport = {},
        onDownloadSource = {},
        onOpenSource = {},
        onCancelImport = {},
        onEnabled = { _, _ -> },
        onMove = { _, _ -> },
        onDelete = {},
    )
}

@Preview(name = "Dictionaries", widthDp = W, heightDp = H)
@Composable
fun DictionariesList() = Dictionaries(false, ImportState.Idle, Samples.dictionaries)

@Preview(name = "Dictionaries dark", widthDp = W, heightDp = H)
@Composable
fun DictionariesListDark() = Dictionaries(true, ImportState.Idle, Samples.dictionaries)

@Preview(name = "Dictionaries importing", widthDp = W, heightDp = H)
@Composable
fun DictionariesImporting() = Dictionaries(
    false,
    ImportState.Importing("Jitendex_2026.zip", "Reading terms 118,000 of 295,132"),
    Samples.dictionaries.take(1),
)

@Preview(name = "Dictionaries copying", widthDp = W, heightDp = H)
@Composable
fun DictionariesCopying() = Dictionaries(
    false,
    ImportState.Copying("Jitendex_2026.zip", 36L * 1024 * 1024, 140L * 1024 * 1024),
    emptyList(),
)

@Preview(name = "Dictionaries done", widthDp = W, heightDp = H)
@Composable
fun DictionariesDone() = Dictionaries(
    false,
    ImportState.Done(ImportSummary(1, "Jitendex", mapOf("terms" to 295_132), 48_000)),
    Samples.dictionaries.take(1),
)

@Preview(name = "Dictionaries empty", widthDp = W, heightDp = H)
@Composable
fun DictionariesEmpty() = Dictionaries(false, ImportState.Idle, emptyList())

@Composable
private fun SettingsPreview(dark: Boolean) = Frame(dark) {
    SettingsContent(
        settings = Settings(),
        dictionarySummary = "2 of 3 on",
        versionName = "2.0.0",
        dynamicColorAvailable = true,
        onTheme = {},
        onDynamicColor = {},
        onFullText = {},
        onOpenDictionaries = {},
        onOpenLink = {},
    )
}

@Preview(name = "Settings", widthDp = W, heightDp = H)
@Composable
fun SettingsLight() = SettingsPreview(false)

@Preview(name = "Settings dark", widthDp = W, heightDp = H)
@Composable
fun SettingsDark() = SettingsPreview(true)

@Preview(name = "App icon", widthDp = 216, heightDp = 216)
@Composable
fun AppIcon() {
    Box(Modifier.fillMaxSize()) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(com.elianiva.jisholens.R.drawable.ic_launcher_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(com.elianiva.jisholens.R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun Lens(state: LensUiState, dark: Boolean = false, showPage: Boolean = true) = Frame(dark) {
    val (bitmap, _) = Samples.screenPage
    Box(Modifier.fillMaxSize().background(PageColor)) {
        if (showPage) {
            Canvas(Modifier.fillMaxSize()) {
                val canvas = IntSize(size.width.toInt(), size.height.toInt())
                val base = fitScale(canvas, bitmap.width, bitmap.height)
                val origin = fitOrigin(canvas, bitmap.width, bitmap.height, base)
                withTransform({
                    translate(origin.x, origin.y)
                    scale(scaleX = base, scaleY = base, pivot = Offset.Zero)
                }) {
                    drawImage(bitmap.asImageBitmap())
                }
            }
        }
        LensOverlayContent(
            state = state,
            screenSize = IntSize(bitmap.width, bitmap.height),
            loadMedia = noMedia,
            onTap = { _, _ -> },
            onSelectCandidate = {},
            onToggleKnown = {},
            onExpand = {},
            onCollapse = {},
            onClearSelection = {},
            onRescan = {},
            onReadImage = {},
            onScroll = {},
            onCopy = {},
            onMine = {},
            onClose = {},
        )
    }
}

private val PageColor = Color(0xFFF6F0E2)

/** The sample page's lines with one box per character, as the text layer would provide. */
private val lensLines: List<LensLine> by lazy {
    Samples.screenPage.second.map { box ->
        val width = box.rect.width().toFloat() / box.text.length
        val characters = box.text.indices.map { index ->
            Rect(
                box.rect.left + (width * index).roundToInt(),
                box.rect.top,
                box.rect.left + (width * (index + 1)).roundToInt(),
                box.rect.bottom,
            )
        }
        LensLine(box.text, box.rect, characters)
    }
}

private fun lensState(
    lines: List<LensLine> = lensLines,
    candidates: List<ScanWord> = emptyList(),
    selectedLine: Int = -1,
    message: LensMessage? = null,
    scanning: Boolean = false,
    expanded: Boolean = false,
    knownWords: Set<String> = emptySet(),
    canReadImage: Boolean = true,
): LensUiState = LensUiState(
    lines = lines,
    scanning = scanning,
    message = message,
    selectedLine = selectedLine,
    candidates = candidates,
    expanded = expanded,
    knownWords = knownWords,
    canReadImage = canReadImage,
)

private val selectedTabeta = ScanWord("食べた", 5, 8, listOf(Samples.tabeta))

@Preview(name = "Lens highlights", widthDp = W, heightDp = H)
@Composable
fun LensHighlights() = Lens(lensState())

@Preview(name = "Lens word", widthDp = W, heightDp = H)
@Composable
fun LensWord() = Lens(lensState(candidates = listOf(selectedTabeta), selectedLine = 1))

@Preview(name = "Lens word dark", widthDp = W, heightDp = H)
@Composable
fun LensWordDark() = Lens(lensState(candidates = listOf(selectedTabeta), selectedLine = 1), dark = true)

@Preview(name = "Lens alternatives", widthDp = W, heightDp = H)
@Composable
fun LensAlternatives() = Lens(
    lensState(
        candidates = listOf(
            ScanWord("食べ", 5, 7, listOf(Samples.taberu)),
            selectedTabeta,
            ScanWord("食べさせられた", 5, 8, listOf(Samples.taberareta)),
        ),
        selectedLine = 1,
    ),
)

@Preview(name = "Lens no entry", widthDp = W, heightDp = H)
@Composable
fun LensNoEntry() = Lens(lensState(selectedLine = 2))

@Preview(name = "Lens loading", widthDp = W, heightDp = H)
@Composable
fun LensLoading() = Lens(lensState(scanning = true))

@Preview(name = "Lens no text", widthDp = W, heightDp = H)
@Composable
fun LensNoText() = Lens(lensState(lines = emptyList(), message = LensMessage.NoText), showPage = false)

@Preview(name = "Lens protected", widthDp = W, heightDp = H)
@Composable
fun LensProtected() = Lens(lensState(lines = emptyList(), message = LensMessage.Protected), showPage = false)

@Preview(name = "Lens full entry", widthDp = W, heightDp = H)
@Composable
fun LensExpanded() = Lens(
    lensState(candidates = listOf(selectedTabeta), selectedLine = 1, expanded = true),
)

@Preview(name = "Lens known words", widthDp = W, heightDp = H)
@Composable
fun LensKnownWords() = Lens(
    lensState(
        candidates = listOf(selectedTabeta),
        selectedLine = 1,
        knownWords = setOf("食べる"),
    ),
)

@Preview(name = "Live reading setup", widthDp = W, heightDp = H)
@Composable
fun LensSetup() = Frame(false) {
    LensSetupScreen(enabled = false, onOpenSettings = {}, onDone = {})
}

@Preview(name = "Live reading setup on", widthDp = W, heightDp = H)
@Composable
fun LensSetupOn() = Frame(false) {
    LensSetupScreen(enabled = true, onOpenSettings = {}, onDone = {})
}

@Preview(name = "Component downloading", widthDp = W, heightDp = 240)
@Composable
fun ComponentDownloading() = Frame(false) {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
        ComponentDownloadPill(
            statuses = listOf(
                ComponentStatus(
                    id = "ocr-japanese",
                    kind = ComponentKind.ENGINE,
                    title = "Japanese text recognition",
                    state = ComponentState.Downloading(45),
                ),
            ),
            onRetry = {},
        )
    }
}

@Preview(name = "Component download failed", widthDp = W, heightDp = 240)
@Composable
fun ComponentDownloadFailed() = Frame(false) {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
        ComponentDownloadPill(
            statuses = listOf(
                ComponentStatus(
                    id = "ocr-japanese",
                    kind = ComponentKind.ENGINE,
                    title = "Japanese text recognition",
                    state = ComponentState.Failed,
                ),
            ),
            onRetry = {},
        )
    }
}
