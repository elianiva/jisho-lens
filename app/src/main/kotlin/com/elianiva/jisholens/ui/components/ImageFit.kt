package com.elianiva.jisholens.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.math.min

/** The scale that fits an image of [width] x [height] inside [canvas], never cropping it. */
fun fitScale(canvas: IntSize, width: Int, height: Int): Float {
    if (canvas.width == 0 || canvas.height == 0 || width == 0 || height == 0) return 1f
    return min(canvas.width.toFloat() / width, canvas.height.toFloat() / height)
}

/** Where the fitted image starts inside [canvas], so the letterbox stays centered. */
fun fitOrigin(canvas: IntSize, width: Int, height: Int, scale: Float): Offset {
    val drawnWidth = width * scale
    val drawnHeight = height * scale
    return Offset((canvas.width - drawnWidth) / 2f, (canvas.height - drawnHeight) / 2f)
}
