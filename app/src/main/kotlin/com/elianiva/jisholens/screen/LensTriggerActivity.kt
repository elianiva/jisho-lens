package com.elianiva.jisholens.screen

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.elianiva.jisholens.JishoLensApp
import com.elianiva.jisholens.MainActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The invisible step between the tile and the overlay.
 *
 * The shade does not collapse instantly, so this activity waits it out and then opens
 * the lens. It is deliberately not the reader: the lens is an accessibility overlay,
 * so the page underneath never becomes a Jisho Lens activity and never has to be
 * recreated for a lookup.
 */
class LensTriggerActivity : ComponentActivity() {
    private val container by lazy { (application as JishoLensApp).container }
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        handler.postDelayed(::trigger, SHADE_COLLAPSE_MILLIS)
    }

    private fun trigger() {
        if (isFinishing || isDestroyed) return
        lifecycleScope.launch {
            container.refreshDictionaries()
            if (container.dictionaries.value.none { it.enabled }) {
                startActivity(Intent(this@LensTriggerActivity, MainActivity::class.java))
                finish()
                return@launch
            }
            val service = LensAccessibilityService.instance ?: withTimeoutOrNull(CONNECT_TIMEOUT_MILLIS) {
                while (LensAccessibilityService.instance == null) delay(POLL_MILLIS)
                LensAccessibilityService.instance
            }
            if (service != null) {
                service.toggleLens()
            } else {
                startActivity(LensSetupActivity.intent(this@LensTriggerActivity))
            }
            finish()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        /** Long enough for the shade to finish collapsing before the page is read. */
        private const val SHADE_COLLAPSE_MILLIS = 250L

        /** A few system bind round-trips after process death, no longer. */
        private const val CONNECT_TIMEOUT_MILLIS = 1_200L
        private const val POLL_MILLIS = 50L

        fun intent(context: Context): Intent = Intent(context, LensTriggerActivity::class.java)
    }
}
