package com.elianiva.jisholens.previews

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.elianiva.jisholens.data.ImportState
import com.elianiva.jisholens.data.Settings
import com.elianiva.jisholens.dictionary.DictionaryRecord
import com.elianiva.jisholens.dictionary.ImportSummary
import com.elianiva.jisholens.ui.components.EntryCard
import com.elianiva.jisholens.ui.components.WithoutDecorativeMotion
import com.elianiva.jisholens.ui.screens.DictionariesContent
import com.elianiva.jisholens.ui.screens.DictionaryStatus
import com.elianiva.jisholens.ui.screens.OnboardingContent
import com.elianiva.jisholens.domain.Entry
import com.elianiva.jisholens.domain.ScanWord
import com.elianiva.jisholens.ui.screens.ReaderLine
import com.elianiva.jisholens.ui.screens.ScanContent
import com.elianiva.jisholens.ui.screens.ScanHomeContent
import com.elianiva.jisholens.ui.screens.ScanUiState
import com.elianiva.jisholens.ui.screens.ScreenScanContent
import com.elianiva.jisholens.ui.screens.ScreenScanMessage
import com.elianiva.jisholens.ui.screens.ScreenScanUiState
import com.elianiva.jisholens.ui.screens.SearchContent
import com.elianiva.jisholens.ui.screens.SearchUiState
import com.elianiva.jisholens.ui.screens.SettingsContent
import com.elianiva.jisholens.ui.theme.JishoLensTheme

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
        importState = ImportState.Downloading("Jitendex", 12L * 1024 * 1024, 38L * 1024 * 1024),
    )
}

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
private fun Screen(
    state: ScreenScanUiState,
    dark: Boolean = false,
) = Frame(dark) {
    ScreenScanContent(
        state = state,
        loadMedia = noMedia,
        onTap = { _, _ -> },
        onSelectCandidate = {},
        onToggleKnown = {},
        onToggleReader = {},
        onSelectReaderWord = { _, _ -> },
        onClearSelection = {},
        onExpand = {},
        onCollapse = {},
        onSearchLine = {},
        onCopy = {},
        onMine = {},
        onRescan = {},
        onClose = {},
    )
}

private fun screenState(
    message: ScreenScanMessage? = null,
    candidates: List<ScanWord> = emptyList(),
    selectedLine: Int = 1,
    cardAtTop: Boolean = false,
    loading: Boolean = false,
    reader: Boolean = false,
    readerLines: List<ReaderLine> = emptyList(),
    expanded: Boolean = false,
    withFrame: Boolean = true,
    knownWords: Set<String> = emptySet(),
): ScreenScanUiState {
    val (bitmap, lines) = Samples.screenPage
    return ScreenScanUiState(
        frame = if (withFrame) bitmap else null,
        lines = if (withFrame) lines else emptyList(),
        loading = loading,
        message = message,
        selectedLine = if (message == null) selectedLine else -1,
        candidates = candidates,
        selectedCandidate = 0,
        reader = reader,
        readerLines = readerLines,
        cardAtTop = cardAtTop,
        expanded = expanded,
        knownWords = knownWords,
    )
}

private val tappedTabeta = ScanWord("食べた", 5, 8, listOf(Samples.tabeta))

@Preview(name = "Screen reader entry", widthDp = W, heightDp = H)
@Composable
fun ScreenReaderEntry() = Screen(screenState(candidates = listOf(tappedTabeta), cardAtTop = true))

@Preview(name = "Screen reader entry dark", widthDp = W, heightDp = H)
@Composable
fun ScreenReaderEntryDark() = Screen(screenState(candidates = listOf(tappedTabeta), cardAtTop = true), dark = true)

@Preview(name = "Screen reader alternatives", widthDp = W, heightDp = H)
@Composable
fun ScreenReaderAlternatives() = Screen(
    screenState(
        candidates = listOf(
            ScanWord("食べ", 5, 7, listOf(Samples.taberu)),
            tappedTabeta,
            ScanWord("食べさせられた", 0, 7, listOf(Samples.taberareta)),
        ),
    ),
    dark = true,
)

@Preview(name = "Screen reader no entry", widthDp = W, heightDp = H)
@Composable
fun ScreenReaderNoEntry() = Screen(screenState())

@Preview(name = "Screen reader hint", widthDp = W, heightDp = H)
@Composable
fun ScreenReaderHint() = Screen(screenState(selectedLine = -1))

@Preview(name = "Screen reader loading", widthDp = W, heightDp = H)
@Composable
fun ScreenReaderLoading() = Screen(screenState(selectedLine = -1, loading = true))

@Preview(name = "Screen reader no text", widthDp = W, heightDp = H)
@Composable
fun ScreenReaderNoText() = Screen(screenState(message = ScreenScanMessage.NoText))

@Preview(name = "Screen reader protected", widthDp = W, heightDp = H)
@Composable
fun ScreenReaderProtected() = Screen(screenState(message = ScreenScanMessage.Protected, withFrame = false))

@Preview(name = "Screen reader consent denied", widthDp = W, heightDp = H)
@Composable
fun ScreenReaderConsent() = Screen(screenState(message = ScreenScanMessage.ConsentDenied, withFrame = false))

@Preview(name = "Screen reader no frame", widthDp = W, heightDp = H)
@Composable
fun ScreenReaderNoFrame() = Screen(screenState(message = ScreenScanMessage.NoFrame, withFrame = false))

@Preview(name = "Screen reader full entry", widthDp = W, heightDp = H)
@Composable
fun ScreenReaderExpanded() = Screen(
    screenState(
        candidates = listOf(tappedTabeta),
        expanded = true,
    ),
)

@Preview(name = "Screen reader page", widthDp = W, heightDp = H)
@Composable
fun ScreenReaderPage() = Screen(
    screenState(
        reader = true,
        selectedLine = -1,
        readerLines = listOf(
            readerLine("昨日、友達と", emptyList()),
            readerLine("ラーメンを食べた。", listOf("ラーメン" to Samples.ramen, "食べた" to Samples.tabeta)),
            readerLine("とても美味しかった。", listOf("美味しかった" to Samples.oishikatta)),
        ),
    ),
)

@Preview(name = "Screen reader known words", widthDp = W, heightDp = H)
@Composable
fun ScreenReaderKnownWords() = Screen(
    screenState(
        reader = true,
        selectedLine = -1,
        knownWords = setOf("食べる", "ラーメン"),
        readerLines = listOf(
            readerLine("昨日、友達と", emptyList()),
            readerLine("ラーメンを食べた。", listOf("ラーメン" to Samples.ramen, "食べた" to Samples.tabeta)),
            readerLine("とても美味しかった。", listOf("美味しかった" to Samples.oishikatta)),
        ),
    ),
)

/** One reader line whose words are placed at their real offsets in the text. */
private fun readerLine(text: String, words: List<Pair<String, Entry>>): ReaderLine =
    ReaderLine(
        text = text,
        rect = android.graphics.Rect(),
        words = words.map { (surface, entry) ->
            val start = text.indexOf(surface).coerceAtLeast(0)
            ScanWord(surface, start, start + surface.length, listOf(entry))
        },
    )
