package com.elianiva.jisholens

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elianiva.jisholens.data.AppContainer
import com.elianiva.jisholens.screen.CaptureTriggerActivity
import com.elianiva.jisholens.screen.ScreenCaptureService
import com.elianiva.jisholens.ui.screens.ScreenScanMessage
import com.elianiva.jisholens.ui.screens.ScreenScanScreen
import com.elianiva.jisholens.ui.theme.JishoLensTheme

/**
 * Hosts the frozen reader over the captured frame.
 *
 * The window is `FLAG_SECURE`: a captured page must not leak into the recents
 * thumbnail or into a screenshot of the reader, and the system screenshot flow
 * would otherwise hand the page to another app. Closing the reader stops the
 * capture session and drops the frame, which is the whole privacy contract.
 */
class ScreenScanActivity : ComponentActivity() {
    private val container: AppContainer
        get() = (application as JishoLensApp).container

    private var message by mutableStateOf<ScreenScanMessage?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        message = intent.message()
        if (container.screenFrames.frame == null && message == null) {
            finish()
            return
        }
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val generation by container.screenFrames.generation.collectAsStateWithLifecycle()
            val dark = settings?.theme?.isDark(isSystemInDarkTheme()) ?: false
            JishoLensTheme(darkTheme = dark, dynamicColor = settings?.dynamicColor ?: true) {
                LaunchedEffect(generation) {
                    if (container.screenFrames.frame != null) message = null
                }
                ScreenScanScreen(
                    container = container,
                    generation = generation,
                    message = message,
                    onRescan = {
                        if (ScreenCaptureService.isRunning) {
                            ScreenCaptureService.capture(this@ScreenScanActivity)
                        } else {
                            startActivity(CaptureTriggerActivity.intent(this@ScreenScanActivity))
                        }
                    },
                    onSearchLine = { text ->
                        if (text.isNotBlank()) {
                            startActivity(
                                Intent(this@ScreenScanActivity, LookupActivity::class.java)
                                    .putExtra(LookupActivity.EXTRA_LOOKUP_TEXT, text),
                            )
                        }
                    },
                    onClose = { finish() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        message = intent.message()
    }

    override fun onDestroy() {
        // A rotation keeps the session; leaving the reader does not.
        if (!isChangingConfigurations) {
            container.screenFrames.clear()
            if (ScreenCaptureService.isRunning) ScreenCaptureService.stop(this)
        }
        super.onDestroy()
    }

    private fun Intent.message(): ScreenScanMessage? {
        val name = getStringExtra(EXTRA_ERROR) ?: return null
        return ScreenScanMessage.entries.firstOrNull { it.name == name }
    }

    companion object {
        const val EXTRA_ERROR = "error"
        const val ERROR_CONSENT = "ConsentDenied"

        fun intent(context: Context, error: String): Intent =
            Intent(context, ScreenScanActivity::class.java).putExtra(EXTRA_ERROR, error)
    }
}
