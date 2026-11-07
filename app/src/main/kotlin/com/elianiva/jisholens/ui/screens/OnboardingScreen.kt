package com.elianiva.jisholens.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elianiva.jisholens.R
import com.elianiva.jisholens.data.AppContainer
import com.elianiva.jisholens.ui.theme.LocalDarkTheme
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    container: AppContainer,
    onImport: () -> Unit,
    onDone: () -> Unit,
) {
    val dictionaries by container.dictionaries.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val dark = LocalDarkTheme.current

    LaunchedEffect(dictionaries) {
        if (dictionaries.isNotEmpty()) {
            container.settings.setOnboardingComplete(true)
            onDone()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Image(
            painter = painterResource(if (dark) R.drawable.logo_light else R.drawable.logo_dark),
            contentDescription = "Jisho Lens",
            modifier = Modifier.height(48.dp),
        )
        Text(
            "Look up Japanese words from any picture.",
            modifier = Modifier.padding(top = 24.dp),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            "Import a Yomitan dictionary archive to start. The app is local: your images and lookups stay on this device.",
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "The old Flutter database is not compatible. Download a Yomitan .zip such as Jitendex or JMdict.",
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onImport, modifier = Modifier.weight(1f)) {
                Text("Import dictionary")
            }
            TextButton(
                onClick = {
                    scope.launch {
                        container.settings.setOnboardingComplete(true)
                        onDone()
                    }
                },
            ) {
                Text("Skip")
            }
        }
    }
}
