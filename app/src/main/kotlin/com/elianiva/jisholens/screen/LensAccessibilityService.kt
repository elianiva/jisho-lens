package com.elianiva.jisholens.screen

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.TileService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.content.ContextCompat
import com.elianiva.jisholens.JishoLensApp
import com.elianiva.jisholens.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The live screen lens.
 *
 * The service is bound by the system after the one-time accessibility grant and does
 * nothing until the Quick Settings tile asks it to show the lens. It reads the active
 * window's text tree and draws an accessibility overlay; the service never holds a
 * MediaProjection session, so there is no consent dialog per lookup and no capture
 * notification.
 */
class LensAccessibilityService : AccessibilityService() {
    private var overlay: LensOverlayHost? = null
    private var opening = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var screenReceiverRegistered = false

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) hideLens()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        if (!screenReceiverRegistered) {
            screenReceiverRegistered = runCatching {
                ContextCompat.registerReceiver(
                    this,
                    screenOff,
                    IntentFilter(Intent.ACTION_SCREEN_OFF),
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
                true
            }.getOrDefault(false)
        }
        requestTileUpdate(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.packageName?.toString() == packageName) return
        val host = overlay ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            -> host.onWindowChanged()
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            -> host.scheduleRefresh()
            else -> Unit
        }
    }

    override fun onInterrupt() {
        hideLens()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlay?.onConfigurationChanged()
    }

    override fun onDestroy() {
        hideLens()
        scope.cancel()
        if (screenReceiverRegistered) {
            runCatching { unregisterReceiver(screenOff) }
            screenReceiverRegistered = false
        }
        instance = null
        super.onDestroy()
    }

    /**
     * Freezes the screen as it is now and opens the lens over it. The screenshot is taken
     * before the overlay exists, so it holds only the page. Where the device cannot take
     * one, the lens opens live over the page instead.
     */
    fun showLens() {
        if (overlay?.isShowing == true || opening) return
        opening = true
        scope.launch {
            try {
                overlay = null
                val host = LensOverlayHost(this@LensAccessibilityService, (application as JishoLensApp).container)
                val page = host.freeze()
                if (host.show(page)) {
                    overlay = host
                    requestTileUpdate(this@LensAccessibilityService)
                } else {
                    page?.recycle()
                }
            } finally {
                opening = false
            }
        }
    }

    /**
     * The Quick Settings tile's way in. It closes the shade with an accessibility action
     * instead of opening an activity, so no Jisho Lens screen flashes up between the page
     * and the lens. The page is captured as soon as the shade has left.
     */
    fun openFromTile() {
        scope.launch {
            val container = (application as JishoLensApp).container
            if (overlay?.isShowing == true) {
                hideLens()
                return@launch
            }
            // The shade closes while the dictionary list loads, so neither waits for the other.
            val dictionaries = async { container.refreshDictionaries() }
            closeShade()
            dictionaries.await()
            if (container.dictionaries.value.none { it.enabled }) {
                startActivity(Intent(this@LensAccessibilityService, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return@launch
            }
            showLens()
        }
    }

    /**
     * Closes the shade or control center and waits until it has left. The shade owns the
     * active window while it is open, so reading before it goes would read SystemUI.
     * Some skins ignore the dismiss action; Back closes those.
     */
    private suspend fun closeShade() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        }
        var elapsed = 0L
        var retried = false
        while (elapsed < SHADE_TIMEOUT_MILLIS && shadeIsActive()) {
            delay(SHADE_POLL_MILLIS)
            elapsed += SHADE_POLL_MILLIS
            if (!retried && elapsed >= SHADE_RETRY_MILLIS) {
                retried = true
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        }
        delay(SHADE_COLLAPSE_MILLIS)
    }

    /** True while a system window such as the shade or control center holds focus. */
    private fun shadeIsActive(): Boolean {
        val active = runCatching { windows.firstOrNull { it.isActive } }.getOrNull() ?: return false
        if (active.type == AccessibilityWindowInfo.TYPE_APPLICATION) return false
        val pkg = runCatching { active.root?.packageName?.toString() }.getOrNull()
        return pkg == null || pkg.contains("systemui", ignoreCase = true)
    }

    internal fun onOverlayHidden(host: LensOverlayHost) {
        if (overlay === host) overlay = null
    }

    fun hideLens() {
        val host = overlay ?: return
        overlay = null
        host.hide()
    }

    fun toggleLens() {
        if (overlay?.isShowing == true) hideLens() else showLens()
    }

    companion object {
        @Volatile
        var instance: LensAccessibilityService? = null
            private set

        val isShowing: Boolean get() = instance?.overlay?.isShowing == true

        /** Long enough for the shade to finish leaving before the page is captured. */
        private const val SHADE_COLLAPSE_MILLIS = 60L
        private const val SHADE_POLL_MILLIS = 25L
        private const val SHADE_RETRY_MILLIS = 450L
        private const val SHADE_TIMEOUT_MILLIS = 2_000L

        /**
         * True after the user has turned the service on in Settings. This reads the stored
         * setting, not the list of bound services: after an update the system rebinds the
         * service a moment late, and the bound list would call it off.
         */
        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, LensAccessibilityService::class.java)
            val stored = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            if (stored.isNullOrEmpty()) return false
            return stored.split(':').any { ComponentName.unflattenFromString(it) == expected }
        }

        fun requestTileUpdate(context: Context) {
            runCatching {
                TileService.requestListeningState(context, ComponentName(context, LensTileService::class.java))
            }
        }
    }
}
