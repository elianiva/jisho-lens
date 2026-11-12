package com.elianiva.jisholens.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The disclosure and the one switch that makes the live lens work.
 *
 * The text is deliberately specific: Play requires an in-app explanation of what the
 * accessibility service can see, what it does with it, and how to turn it off. The
 * lens is not an accessibility tool, so the disclosure is always shown before the
 * system grant, not hidden in a privacy policy.
 */
@Composable
fun LensSetupScreen(
    enabled: Boolean,
    onOpenSettings: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Live reading") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = if (enabled) Icons.Rounded.CheckCircle else Icons.Rounded.CenterFocusStrong,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = if (enabled) "Live reading is on" else "Turn on live reading",
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                )
            }

            Text(
                text = if (enabled) {
                    "Tap the Screen lens tile in Quick settings over any app. Words highlight in place; tap one to see its entry."
                } else {
                    "Jisho Lens uses Android's accessibility service to read Japanese text from the app on your screen and show dictionary entries. Text is read on this device and is never stored, sent, or shared."
                },
                style = MaterialTheme.typography.bodyLarge,
            )

            Surface(
                shape = MaterialTheme.shapes.largeIncreased,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("What the service can see", style = MaterialTheme.typography.titleSmallEmphasized)
                    Text(
                        "It can read the text and window bounds of the app in front, and take a screenshot only when you ask it to read an image or when that app exposes no text. It does not read passwords. Nothing leaves the device.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "The lens is open only after you tap the tile. It closes when you close it, when the screen turns off, or after ten minutes without use.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (enabled) {
                Text("You can turn it off at any time in Settings > Accessibility.", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text("Done")
                }
            } else {
                Text(
                    "In Accessibility, open Jisho Lens and turn on Live reading.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Continue to accessibility settings")
                }
                TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text("Not now")
                }
            }
        }
    }
}
