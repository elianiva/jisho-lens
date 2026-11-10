package com.elianiva.jisholens.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileOpen
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elianiva.jisholens.data.AppContainer
import com.elianiva.jisholens.ui.components.LargeActionButton
import com.elianiva.jisholens.ui.components.ShapeBadge
import com.elianiva.jisholens.ui.theme.JapaneseLocale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun OnboardingScreen(
    container: AppContainer,
    onImport: () -> Unit,
    onDone: () -> Unit,
) {
    val dictionaries by container.dictionaries.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(dictionaries) {
        if (dictionaries.isNotEmpty()) {
            container.settings.setOnboardingComplete(true)
            withContext(Dispatchers.Main.immediate) { onDone() }
        }
    }

    OnboardingContent(
        onImport = onImport,
        onSkip = {
            scope.launch {
                container.settings.setOnboardingComplete(true)
                withContext(Dispatchers.Main.immediate) { onDone() }
            }
        },
    )
}

/** First run. Importing a dictionary is the one thing a new person needs, so it is the one big button. */
@Composable
fun OnboardingContent(onImport: () -> Unit, onSkip: () -> Unit) {
    Box(Modifier.fillMaxSize().systemBarsPadding()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.weight(1f, fill = true).padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                ShapeBadge(
                    polygon = MaterialShapes.Cookie12Sided,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    size = 168.dp,
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
                    modifier = Modifier.padding(top = 32.dp),
                    style = MaterialTheme.typography.displaySmallEmphasized,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Import a Yomitan dictionary, such as Jitendex or JMdict.",
                    modifier = Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            LargeActionButton(
                text = "Import dictionary",
                icon = Icons.Rounded.FileOpen,
                onClick = onImport,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = onSkip, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
                Text("Skip for now")
            }
        }
    }
}
