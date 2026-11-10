package com.elianiva.jisholens.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elianiva.jisholens.data.AppContainer
import com.elianiva.jisholens.data.DictionarySource
import com.elianiva.jisholens.data.ImportState
import com.elianiva.jisholens.ui.components.DictionarySourceList
import com.elianiva.jisholens.ui.components.ImportProgressCard
import com.elianiva.jisholens.ui.components.ShapeBadge
import com.elianiva.jisholens.ui.components.rememberDictionaryDownloader
import com.elianiva.jisholens.ui.components.rememberDictionaryImporter
import com.elianiva.jisholens.ui.theme.JapaneseLocale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun OnboardingScreen(
    container: AppContainer,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val dictionaries by container.dictionaries.collectAsStateWithLifecycle()
    val importState by container.importController.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val importFile = rememberDictionaryImporter()
    val download = rememberDictionaryDownloader()

    LaunchedEffect(dictionaries) {
        if (dictionaries.isNotEmpty()) {
            container.settings.setOnboardingComplete(true)
            withContext(Dispatchers.Main.immediate) { onDone() }
        }
    }

    OnboardingContent(
        importState = importState,
        onImport = importFile,
        onDownload = download,
        onOpenPage = { source -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source.pageUrl))) },
        onCancelImport = container.importController::requestCancel,
        onSkip = {
            scope.launch {
                container.settings.setOnboardingComplete(true)
                withContext(Dispatchers.Main.immediate) { onDone() }
            }
        },
    )
}

/**
 * First run. A new reader needs one thing: a dictionary. Downloading one is the
 * shortest path, so the catalogue is on this screen with the size and the licence,
 * and importing a file is the secondary action.
 */
@Composable
fun OnboardingContent(
    onImport: () -> Unit,
    onSkip: () -> Unit,
    importState: ImportState = ImportState.Idle,
    onDownload: (DictionarySource) -> Unit = {},
    onOpenPage: (DictionarySource) -> Unit = {},
    onCancelImport: () -> Unit = {},
) {
    val busy = importState is ImportState.Downloading ||
        importState is ImportState.Copying ||
        importState is ImportState.Importing
    Box(Modifier.fillMaxSize().systemBarsPadding()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                ShapeBadge(
                    polygon = MaterialShapes.Cookie12Sided,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    size = 128.dp,
                    spinSeconds = 40,
                ) {
                    Text(
                        text = "辞",
                        style = MaterialTheme.typography.displayLargeEmphasized.copy(localeList = JapaneseLocale),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Text(
                    text = "Read Japanese\nfrom any picture",
                    modifier = Modifier.padding(top = 24.dp),
                    style = MaterialTheme.typography.displaySmallEmphasized,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Start with a dictionary. Everything stays on this device.",
                    modifier = Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                text = "Start with a dictionary",
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 8.dp),
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
            if (busy) {
                ImportProgressCard(
                    state = importState,
                    onCancel = onCancelImport,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                DictionarySourceList(onDownload = onDownload, onOpenPage = onOpenPage)
            }
            TextButton(onClick = onImport, modifier = Modifier.padding(top = 4.dp)) {
                Text("Import a downloaded file")
            }
            TextButton(onClick = onSkip, modifier = Modifier.padding(bottom = 8.dp)) {
                Text("Skip for now")
            }
        }
    }
}
