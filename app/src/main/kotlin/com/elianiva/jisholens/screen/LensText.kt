package com.elianiva.jisholens.screen

import android.graphics.Rect
import android.graphics.RectF
import com.elianiva.jisholens.dictionary.FtsText
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * One text node read from the accessibility tree, before it is split into lines.
 *
 * [characterBounds] is the optional per-character geometry Android can return for a
 * text node. It is in screen pixels and has one entry per character of [text]. It is
 * empty when the app does not expose character locations, which is common for web
 * content and custom-rendered text.
 */
data class LensNode(
    val text: String,
    val bounds: Rect,
    val characterBounds: List<RectF?> = emptyList(),
    val textSizePx: Float? = null,
)

/**
 * One line of Japanese text, with the box of every character when the source knows it.
 *
 * [characters] is either empty or exactly [text].length long. A non-empty list is what
 * makes a tap land on the character under the finger; the fallback maps the tap across
 * the whole line. An accessibility block that wraps several lines is split before it
 * reaches here, so a highlight always follows a line, not a paragraph.
 */
data class LensLine(
    val text: String,
    val rect: Rect,
    val characters: List<Rect> = emptyList(),
)

/**
 * Turns the text nodes of the active window into the lines the lens draws.
 *
 * A node whose text contains its child's text is kept and the child is dropped: the
 * parent carries the whole line, while a `TextView` span or a web inline node would
 * only duplicate part of it. When the node has no character geometry, newlines and,
 * for nodes with a known text size, the width decide where lines break.
 */
internal fun buildLensLines(nodes: List<LensNode>): List<LensLine> {
    val candidates = nodes
        .mapNotNull { it.trimmed().takeIf(::isReadable) }
        .sortedByDescending { it.bounds.width().toLong() * it.bounds.height() }

    val kept = ArrayList<LensNode>(candidates.size)
    for (node in candidates) {
        val duplicate = kept.any { parent ->
            parent.text.length >= node.text.length &&
                parent.text.contains(node.text) &&
                parent.bounds.covers(node.bounds)
        }
        if (!duplicate) kept += node
    }

    return kept
        .flatMap { it.lines() }
        .filter { line -> line.text.any { FtsText.isJapanese(it.code) } }
        .sortedWith(compareBy({ it.rect.top }, { it.rect.left }))
        .distinctBy { "${it.rect}|${it.text}" }
}

/** The line under ([x], [y]), or the closest line within one line height. */
internal fun lensLineAt(lines: List<LensLine>, x: Float, y: Float): Int {
    lines.forEachIndexed { index, line ->
        if (x >= line.rect.left && x <= line.rect.right && y >= line.rect.top && y <= line.rect.bottom) {
            return index
        }
    }
    if (lines.isEmpty()) return -1
    val nearest = lines.indices.minBy { abs(lines[it].rect.centerY() - y) }
    val line = lines[nearest]
    return if (abs(line.rect.centerY() - y) <= max(line.rect.height().toFloat(), 24f)) nearest else -1
}

/**
 * The character of this line under [x]. When the source gave character boxes, the box
 * under the finger wins; otherwise the position is proportional across the line.
 */
internal fun LensLine.characterAt(x: Float, y: Float): Int {
    if (text.isEmpty()) return 0
    if (characters.size == text.length) {
        val inside = characters.indexOfFirst { x >= it.left && x <= it.right && y >= it.top && y <= it.bottom }
        if (inside >= 0) return inside
        return characters.indices.minBy { index ->
            abs(characters[index].centerX() - x) + abs(characters[index].centerY() - y) / 4f
        }
    }
    if (rect.width() <= 0) return 0
    val ratio = ((x - rect.left) / rect.width()).coerceIn(0f, 0.999f)
    return (ratio * text.length).toInt().coerceIn(0, text.lastIndex)
}

/** The nearest non-space character, so a tap in the gap between words still finds one. */
internal fun LensLine.nearestWordOffset(offset: Int): Int {
    if (text.isEmpty() || !text[offset].isWhitespace()) return offset
    for (distance in 1..text.length) {
        val left = offset - distance
        if (left >= 0 && !text[left].isWhitespace()) return left
        val right = offset + distance
        if (right < text.length && !text[right].isWhitespace()) return right
    }
    return offset
}

/** The box around the characters [start] until [end], for the selected word. */
internal fun LensLine.wordRect(start: Int, end: Int): Rect {
    val from = start.coerceIn(0, text.length)
    val to = end.coerceIn(from, text.length)
    if (from == to) return rect
    if (characters.size == text.length) {
        val selected = characters.subList(from, to)
        return Rect(
            selected.minOf { it.left },
            selected.minOf { it.top },
            selected.maxOf { it.right },
            selected.maxOf { it.bottom },
        )
    }
    if (text.isEmpty() || rect.width() <= 0) return rect
    val left = rect.left + (rect.width() * from / text.length.toFloat()).roundToInt()
    val right = rect.left + (rect.width() * to / text.length.toFloat()).roundToInt()
    return Rect(left, rect.top, max(left + 1, right), rect.bottom)
}

private fun isReadable(node: LensNode): Boolean =
    node.text.isNotBlank() &&
        !node.bounds.isEmpty &&
        node.bounds.width() > 0 &&
        node.bounds.height() > 0 &&
        node.text.any { FtsText.isJapanese(it.code) }

private fun LensNode.trimmed(): LensNode {
    val start = text.indexOfFirst { !it.isWhitespace() }
    if (start < 0) return copy(text = "")
    val end = text.indexOfLast { !it.isWhitespace() } + 1
    val characters = if (characterBounds.size == text.length) {
        characterBounds.subList(start, end).toList()
    } else {
        emptyList()
    }
    return copy(
        text = text.substring(start, end),
        characterBounds = characters,
    )
}

private fun LensNode.lines(): List<LensLine> {
    exactLines()?.let { if (it.isNotEmpty()) return it }
    return approximateLines()
}

/**
 * Splits a node by its real character boxes. A run starts a new line when the next
 * character's center moves by more than half a character height, which catches both
 * explicit newlines and wrapped text without trusting the app's text to contain them.
 */
private fun LensNode.exactLines(): List<LensLine>? {
    if (characterBounds.size != text.length) return null
    val known = characterBounds.count { it != null }
    if (known == 0) return null
    // A couple of missing boxes can be filled in; a mostly empty array means the app
    // advertised the data but could not produce it, so use the rectangular fallback.
    if (known < text.count { !it.isWhitespace() } * 0.6f) return null

    val runs = ArrayList<IntRange>()
    var start = 0
    var lastCenter = Float.NaN
    var lastHeight = 0f
    for (index in text.indices) {
        if (text[index] == '\n') {
            if (index > start) runs += start until index
            start = index + 1
            lastCenter = Float.NaN
            lastHeight = 0f
            continue
        }
        val box = characterBounds[index] ?: continue
        val height = box.height().takeIf { it > 0f } ?: 1f
        val center = box.centerY()
        if (!lastCenter.isNaN() && abs(center - lastCenter) > max(height, lastHeight) * 0.6f) {
            if (index > start) runs += start until index
            start = index
        }
        lastCenter = center
        lastHeight = height
    }
    if (text.length > start) runs += start until text.length

    return runs.mapNotNull { range -> lineOf(range) }
}

private fun LensNode.lineOf(range: IntRange): LensLine? {
    var start = range.first
    var end = range.last + 1
    while (start < end && text[start].isWhitespace()) start++
    while (end > start && text[end - 1].isWhitespace()) end--
    if (start >= end) return null

    val boxes = ArrayList<Rect?>(end - start)
    for (index in start until end) {
        boxes += characterBounds[index]?.let {
            Rect(it.left.roundToInt(), it.top.roundToInt(), it.right.roundToInt(), it.bottom.roundToInt())
        }
    }
    val filled = fillMissing(boxes) ?: return null
    val left = filled.minOf { it.left }
    val top = filled.minOf { it.top }
    val right = filled.maxOf { it.right }
    val bottom = filled.maxOf { it.bottom }
    return LensLine(
        text = text.substring(start, end),
        rect = Rect(left, top, right, bottom),
        characters = filled,
    )
}

/** Fills characters the app left without a box, by copying or interpolating neighbours. */
private fun fillMissing(boxes: List<Rect?>): List<Rect>? {
    if (boxes.isEmpty()) return null
    val result = ArrayList<Rect>(boxes.size)
    boxes.forEachIndexed { index, box ->
        if (box != null) {
            result += box
            return@forEachIndexed
        }
        val previous = (index - 1 downTo 0).firstNotNullOfOrNull { boxes[it] }
        val next = (index + 1 until boxes.size).firstNotNullOfOrNull { boxes[it] }
        val filled = when {
            previous != null -> Rect(previous.right, previous.top, previous.right + previous.width(), previous.bottom)
            next != null -> Rect(next.left - next.width(), next.top, next.left, next.bottom)
            else -> return null
        }
        result += filled
    }
    return result.takeIf { it.all { box -> box.width() > 0 && box.height() > 0 } }
}

private fun LensNode.approximateLines(): List<LensLine> {
    val rows = text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    if (rows.size > 1) {
        val height = bounds.height().toFloat() / rows.size
        return rows.mapIndexedNotNull { index, row ->
            val top = bounds.top + (height * index).roundToInt()
            val bottom = if (index == rows.lastIndex) bounds.bottom else bounds.top + (height * (index + 1)).roundToInt()
            rowLine(row, Rect(bounds.left, top, bounds.right, max(top + 1, bottom)))
        }
    }

    val line = rows.singleOrNull() ?: return emptyList()
    val size = textSizePx
    if (size != null && size > 0f) {
        val perRow = floor(bounds.width() / size).toInt().coerceAtLeast(1)
        if (perRow < line.length) {
            val rowCount = ceil(line.length / perRow.toFloat()).toInt()
            val height = bounds.height().toFloat() / rowCount
            return (0 until rowCount).mapNotNull { index ->
                val from = index * perRow
                val to = (from + perRow).coerceAtMost(line.length)
                if (from >= to) return@mapNotNull null
                val top = bounds.top + (height * index).roundToInt()
                val bottom = if (index == rowCount - 1) bounds.bottom else bounds.top + (height * (index + 1)).roundToInt()
                rowLine(line.substring(from, to), Rect(bounds.left, top, bounds.right, max(top + 1, bottom)))
            }
        }
    }
    return listOfNotNull(rowLine(line, bounds))
}

/** One approximated line: characters share the width evenly, so a tap maps to the right one. */
private fun rowLine(text: String, rect: Rect): LensLine? {
    if (text.isEmpty() || rect.width() <= 0 || rect.height() <= 0) return null
    val width = rect.width().toFloat() / text.length
    val characters = text.indices.map { index ->
        val left = rect.left + (width * index).roundToInt()
        val right = rect.left + (width * (index + 1)).roundToInt()
        Rect(left, rect.top, max(left + 1, right), rect.bottom)
    }
    return LensLine(text = text, rect = rect, characters = characters)
}

/** True when this rectangle contains [other], allowing for a pixel of layout rounding. */
private fun Rect.covers(other: Rect): Boolean =
    left <= other.left + 2 && top <= other.top + 2 && right >= other.right - 2 && bottom >= other.bottom - 2
