package com.elianiva.jisholens.screen

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.service.quicksettings.TileService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import com.elianiva.jisholens.JishoLensApp

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
        if (screenReceiverRegistered) {
            runCatching { unregisterReceiver(screenOff) }
            screenReceiverRegistered = false
        }
        instance = null
        super.onDestroy()
    }

    fun showLens() {
        if (overlay?.isShowing == true) return
        overlay = null
        val host = LensOverlayHost(this, (application as JishoLensApp).container)
        if (host.show()) {
            overlay = host
            requestTileUpdate(this)
        }
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

        /** True after the user has turned the service on in Settings. */
        fun isEnabled(context: Context): Boolean {
            val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
            val expected = ComponentName(context, LensAccessibilityService::class.java)
            return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { info ->
                    val serviceInfo = info.resolveInfo?.serviceInfo ?: return@any false
                    serviceInfo.packageName == expected.packageName &&
                        serviceInfo.name.orEmpty().endsWith(expected.className.substringAfterLast('.'))
                }
        }

        fun requestTileUpdate(context: Context) {
            runCatching {
                TileService.requestListeningState(context, ComponentName(context, LensTileService::class.java))
            }
        }
    }
}
