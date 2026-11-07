package com.elianiva.jisholens

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.IntentCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.elianiva.jisholens.data.AppContainer
import com.elianiva.jisholens.ui.navigation.AppRoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    private val pendingScanPath = mutableStateOf<String?>(null)
    private val container: AppContainer
        get() = (application as JishoLensApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            AppRoot(container = container, pendingScanPath = pendingScanPath.value)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action != Intent.ACTION_SEND) return
        val type = intent.type.orEmpty()
        when {
            type.startsWith("image/") -> {
                val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?: return
                lifecycleScope.launch {
                    val path = withContext(Dispatchers.IO) { copySharedImage(uri) }
                    pendingScanPath.value = path
                }
            }
            type == "text/plain" -> {
                val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
                if (text.isNotBlank()) openLookup(text)
            }
        }
    }

    private fun openLookup(text: String) {
        startActivity(
            Intent(this, LookupActivity::class.java)
                .putExtra(LookupActivity.EXTRA_LOOKUP_TEXT, text),
        )
    }

    private fun copySharedImage(uri: Uri): String? = runCatching {
        val directory = File(cacheDir, "scans").apply { mkdirs() }
        val target = File(directory, "shared-${System.currentTimeMillis()}.jpg")
        val input = contentResolver.openInputStream(uri)
            ?: error("The shared image could not be read")
        input.use { source ->
            target.outputStream().use { sink -> source.copyTo(sink) }
        }
        target.absolutePath
    }.getOrNull()
}
