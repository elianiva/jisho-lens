package com.elianiva.jisholens.screen

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.elianiva.jisholens.JishoLensApp
import com.elianiva.jisholens.MainActivity
import com.elianiva.jisholens.ScreenScanActivity
import com.elianiva.jisholens.ui.screens.ScreenScanMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The invisible step between the tile and the capture.
 *
 * Tapping the tile collapses the shade, but not instantly. This activity is
 * translucent and draws nothing, so the app the reader was looking at stays on
 * screen; after the shade has gone it either asks the system for consent or, when
 * a reading session is already alive, captures another frame with no dialog.
 *
 * It also stays alive until the frame arrives, then opens the reader itself. A
 * service cannot start an activity once nothing of ours is on screen: Android 10
 * blocks background activity launches, and the trigger is what keeps the app in the
 * foreground through the capture.
 */
class CaptureTriggerActivity : ComponentActivity() {
    private val container by lazy { (application as JishoLensApp).container }
    private val handler = Handler(Looper.getMainLooper())

    private val consent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        when {
            result.resultCode == Activity.RESULT_OK && data != null -> {
                val before = container.screenFrames.generation.value
                ScreenCaptureService.start(this, result.resultCode, data)
                awaitFrame(before)
            }
            else -> {
                startActivity(ScreenScanActivity.intent(this, ScreenScanActivity.ERROR_CONSENT))
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            // The consent dialog recreated us; the result is on its way.
            return
        }
        handler.postDelayed(::trigger, SHADE_COLLAPSE_MILLIS)
    }

    private fun trigger() {
        if (isFinishing || isDestroyed) return
        lifecycleScope.launch {
            container.refreshDictionaries()
            if (container.dictionaries.value.none { it.enabled }) {
                // Without a dictionary a capture would be a dead end; the app opens on
                // its import screen instead.
                startActivity(Intent(this@CaptureTriggerActivity, MainActivity::class.java))
                finish()
                return@launch
            }
            if (ScreenCaptureService.isRunning) {
                val before = container.screenFrames.generation.value
                ScreenCaptureService.capture(this@CaptureTriggerActivity)
                awaitFrame(before)
                return@launch
            }
            val manager = getSystemService(MediaProjectionManager::class.java)
            consent.launch(manager.createScreenCaptureIntent())
        }
    }

    /** Waits for the capture, then opens the reader while this activity is still visible. */
    private fun awaitFrame(before: Long) {
        lifecycleScope.launch {
            val arrived = withTimeoutOrNull(FRAME_TIMEOUT_MILLIS) {
                container.screenFrames.generation.first { it > before }
            }
            val message = if (arrived == null) ScreenScanMessage.NoFrame.name else null
            startActivity(
                if (message == null) {
                    Intent(this@CaptureTriggerActivity, ScreenScanActivity::class.java)
                } else {
                    ScreenScanActivity.intent(this@CaptureTriggerActivity, message)
                },
            )
            finish()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        /**
         * Long enough for the shade to finish collapsing. A frame taken while it is
         * still animating shows the shade, not the page.
         */
        private const val SHADE_COLLAPSE_MILLIS = 250L

        /** Above the one-second capture budget, below the point where waiting feels broken. */
        private const val FRAME_TIMEOUT_MILLIS = 4_000L

        fun intent(context: Context): Intent = Intent(context, CaptureTriggerActivity::class.java)
    }
}
