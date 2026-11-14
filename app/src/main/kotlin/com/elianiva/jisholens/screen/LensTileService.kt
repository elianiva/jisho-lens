package com.elianiva.jisholens.screen

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.elianiva.jisholens.R

/**
 * The way in while another app is on screen.
 *
 * With live reading running on Android 12 or later, the tile asks the service to close
 * the shade and open the lens, with no activity in between. On older versions it opens
 * [LensTriggerActivity] so the shade has time to collapse. Without the grant it opens
 * the disclosure and the system accessibility settings once; after that the tile does
 * not ask again.
 */
class LensTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = if (LensAccessibilityService.isShowing) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = getString(R.string.screen_lens_tile)
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val service = LensAccessibilityService.instance
        if (service != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            service.openFromTile()
            return
        }
        val intent = if (LensAccessibilityService.isEnabled(this)) {
            LensTriggerActivity.intent(this)
        } else {
            LensSetupActivity.intent(this)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            @SuppressLint("StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}
