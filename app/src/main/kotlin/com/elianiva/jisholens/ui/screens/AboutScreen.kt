package com.elianiva.jisholens.ui.screens

import android.content.ActivityNotFoundException
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.elianiva.jisholens.BuildConfig
import com.elianiva.jisholens.R
import com.elianiva.jisholens.ui.theme.LocalDarkTheme

@Composable
fun AboutScreen() {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val dark = LocalDarkTheme.current
    val logo = if (dark) R.drawable.logo_light else R.drawable.logo_dark
    val github = if (dark) R.drawable.github_light else R.drawable.github_dark

    fun open(url: String) {
        try {
            uriHandler.openUri(url)
        } catch (_: IllegalArgumentException) {
            android.widget.Toast.makeText(context, "No app can open this link", android.widget.Toast.LENGTH_SHORT).show()
        } catch (_: ActivityNotFoundException) {
            android.widget.Toast.makeText(context, "No app can open this link", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Image(
            painter = painterResource(logo),
            contentDescription = "Jisho Lens",
            modifier = Modifier.height(36.dp),
        )
        Text(
            "Jisho Lens is a Japanese dictionary that scans text from a picture and looks up the words locally.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Text recognition uses Google ML Kit. Dictionary data comes from Yomitan-compatible dictionaries. " +
                "The app does not send your images or lookups to a server.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Version ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { open("https://github.com/elianiva/jisho-lens/issues") }) {
                Icon(Icons.Outlined.BugReport, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                Text("Report an issue")
            }
            OutlinedButton(onClick = { open("https://github.com/elianiva/jisho-lens") }) {
                Image(
                    painter = painterResource(github),
                    contentDescription = null,
                    modifier = Modifier.height(18.dp).padding(end = 6.dp),
                )
                Text("GitHub")
            }
        }
        Button(
            onClick = { open("https://github.com/elianiva/jisho-lens/blob/master/THIRD_PARTY_NOTICES.md") },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
            Text("Licenses and attribution")
        }
        Text(
            "Dictionary data is provided by its authors. Jitendex and JMdict are licensed under CC BY-SA 4.0. " +
                "The deconjugation rules are Apache-2.0.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
