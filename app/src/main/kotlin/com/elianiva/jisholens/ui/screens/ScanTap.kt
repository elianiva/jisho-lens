package com.elianiva.jisholens.ui.screens

import android.graphics.Rect
import com.elianiva.jisholens.data.TextLineBox
import kotlin.math.abs

/**
 * The character of [line] under [x]. ML Kit gives one box per element (usually a
 * character) for Japanese, so the element under the finger wins; without boxes the
 * position is estimated from the tap ratio across the line.
 */
internal fun charIndexAt(line: TextLineBox, x: Float?): Int {
    if (x == null || line.rect.width() <= 0) return 0
    val element = line.elements.indexOfFirst { x >= it.left && x <= it.right }
    if (element >= 0) return element.coerceAtMost(line.text.lastIndex.coerceAtLeast(0))
    val ratio = ((x - line.rect.left) / line.rect.width()).coerceIn(0f, 0.999f)
    return (ratio * line.text.length).toInt().coerceIn(0, line.text.lastIndex.coerceAtLeast(0))
}

/**
 * The line under ([x], [y]), or the closest line within one line height. A finger
 * rarely lands inside a thin line box, so a tap between lines still selects the
 * line it meant; a tap far from every line selects nothing.
 */
internal fun lineAt(lines: List<TextLineBox>, x: Float, y: Float): Int {
    lines.forEachIndexed { index, line ->
        if (x >= line.rect.left && x <= line.rect.right && y >= line.rect.top && y <= line.rect.bottom) {
            return index
        }
    }
    if (lines.isEmpty()) return -1
    val nearest = lines.indices.minBy { abs(lines[it].rect.centerY() - y) }
    val line = lines[nearest]
    return if (abs(line.rect.centerY() - y) <= line.rect.height()) nearest else -1
}

/** True when the card belongs above the tapped line: the line is in the bottom 45% of the frame. */
internal fun cardAtTop(line: Rect, frameHeight: Int): Boolean =
    frameHeight > 0 && line.centerY() > frameHeight * 0.55f
