package com.elianiva.jisholens.screen

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.elianiva.jisholens.R

/**
 * The way in while another app is on screen.
 *
 * A menu item or a launcher shortcut cannot do this: opening either of them makes
 * Jisho Lens the foreground app, so the capture would show Jisho Lens. Tapping a
 * tile collapses the shade and hands control to [CaptureTriggerActivity], which is
 * transparent, so the frame is still the page the reader was reading.
 */
class ScreenScanTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = if (ScreenCaptureService.isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = getString(R.string.screen_lens_tile)
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val intent = CaptureTriggerActivity.intent(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14 asks for a PendingIntent so the system can collapse the shade
            // itself before the activity appears.
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
            startActivityAndCollapse(intent)
        }
    }
}
