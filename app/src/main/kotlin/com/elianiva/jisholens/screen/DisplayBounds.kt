package com.elianiva.jisholens.screen

import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.view.WindowManager

/**
 * The whole display in screen coordinates, including the system bars.
 *
 * Accessibility bounds are screen coordinates from (0, 0), and the lens overlay covers
 * the whole display, so the app's usable area is the wrong frame: it would scale the
 * highlights and leave them off the text.
 */
internal fun Context.displayBounds(): Rect {
    val manager = getSystemService(WindowManager::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        return manager.currentWindowMetrics.bounds
    }
    val point = Point()
    @Suppress("DEPRECATION")
    manager.defaultDisplay.getRealSize(point)
    return Rect(0, 0, point.x, point.y)
}
