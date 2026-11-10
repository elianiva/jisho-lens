package com.elianiva.jisholens

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elianiva.jisholens.data.AppContainer
import com.elianiva.jisholens.ui.screens.SearchScreen
import com.elianiva.jisholens.ui.theme.JishoLensTheme

class LookupActivity : ComponentActivity() {
    private val container: AppContainer
        get() = (application as JishoLensApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            ?: intent.getStringExtra(EXTRA_LOOKUP_TEXT)
        if (text.isNullOrBlank()) {
            finish()
            return
        }
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val dark = settings?.theme?.isDark(androidx.compose.foundation.isSystemInDarkTheme()) ?: false
            JishoLensTheme(darkTheme = dark, dynamicColor = settings?.dynamicColor ?: true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    SearchScreen(
                        container = container,
                        onOpenDictionaries = null,
                        initialQuery = text,
                        onClose = { finish() },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    companion object {
        const val EXTRA_LOOKUP_TEXT = "lookup_text"
    }
}
