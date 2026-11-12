package com.elianiva.jisholens.screen

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elianiva.jisholens.JishoLensApp
import com.elianiva.jisholens.ui.screens.LensSetupScreen
import com.elianiva.jisholens.ui.theme.JishoLensTheme

/**
 * The one-time disclosure for the accessibility service.
 *
 * Play requires an in-app explanation of what an accessibility service reads and how
 * it is used before the user turns it on, and the system accessibility screen is the
 * only place the grant can be made. The screen also shows the live state when the user
 * comes back from Settings.
 */
class LensSetupActivity : ComponentActivity() {
    private val container by lazy { (application as JishoLensApp).container }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            var enabled by remember { mutableStateOf(LensAccessibilityService.isEnabled(this)) }
            val owner = LocalLifecycleOwner.current
            val context = LocalContext.current
            DisposableEffect(owner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        enabled = LensAccessibilityService.isEnabled(context)
                    }
                }
                owner.lifecycle.addObserver(observer)
                onDispose { owner.lifecycle.removeObserver(observer) }
            }
            JishoLensTheme(
                darkTheme = settings?.theme?.isDark(isSystemInDarkTheme()) ?: false,
                dynamicColor = settings?.dynamicColor ?: true,
            ) {
                LensSetupScreen(
                    enabled = enabled,
                    onOpenSettings = ::openAccessibilitySettings,
                    onDone = ::finish,
                )
            }
        }
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "This device has no accessibility settings", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, LensSetupActivity::class.java)
    }
}
