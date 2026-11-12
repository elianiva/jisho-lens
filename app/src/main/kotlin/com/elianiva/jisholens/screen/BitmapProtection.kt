package com.elianiva.jisholens.screen

import android.graphics.Bitmap

/**
 * A window that hides its pixels, such as a banking app, DRM video, or the lock screen,
 * comes back as one flat, usually black, color. OCR would report no text, which is true
 * but misleading, so the lens says the screen is protected instead.
 *
 * Accessibility screenshots already fail with a secure-window error in most of these
 * cases; this catches the ones that return a blank bitmap instead.
 */
internal fun Bitmap.isProtected(): Boolean {
    if (width <= 0 || height <= 0) return true
    val stepX = (width / SAMPLE_COLUMNS).coerceAtLeast(1)
    val stepY = (height / SAMPLE_COLUMNS).coerceAtLeast(1)
    var first = 0
    var sampled = 0
    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            val pixel = getPixel(x, y)
            if (sampled == 0) first = pixel else if (pixel != first) return false
            sampled++
            x += stepX
        }
        y += stepY
    }
    val red = (first shr 16) and 0xFF
    val green = (first shr 8) and 0xFF
    val blue = first and 0xFF
    return red < DARK_THRESHOLD && green < DARK_THRESHOLD && blue < DARK_THRESHOLD
}

private const val SAMPLE_COLUMNS = 16
private const val DARK_THRESHOLD = 16
