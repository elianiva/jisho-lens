package com.elianiva.jisholens.dictionary

/** A run of the headword and, when present, its reading above that run. */
data class RubySegment(val text: String, val reading: String?)

/**
 * Aligns a Japanese expression with its reading for ruby display.
 *
 * The common case is okurigana: the kana around a kanji run must match the
 * reading exactly, and the reading left between them belongs to the kanji. If
 * the strings cannot be aligned, the caller gets one segment with the whole
 * reading above the whole expression. That is less pretty than per-kanji ruby,
 * but it never guesses and never drops characters.
 */
object Furigana {
    fun align(expression: String, reading: String): List<RubySegment> {
        if (expression.isEmpty()) return emptyList()
        if (reading.isEmpty() || expression == reading) {
            return listOf(RubySegment(expression, null))
        }
        if (expression.none { isKanji(it) }) {
            return listOf(RubySegment(expression, null))
        }

        val segments = ArrayList<RubySegment>()
        var expressionIndex = 0
        var readingIndex = 0
        while (expressionIndex < expression.length) {
            val character = expression[expressionIndex]
            if (!isKanji(character)) {
                if (readingIndex >= reading.length || reading[readingIndex] != character) {
                    return fallback(expression, reading)
                }
                segments.add(RubySegment(character.toString(), null))
                expressionIndex++
                readingIndex++
                continue
            }

            val kanjiStart = expressionIndex
            while (expressionIndex < expression.length && isKanji(expression[expressionIndex])) {
                expressionIndex++
            }
            val kanjiRun = expression.substring(kanjiStart, expressionIndex)
            // The kana after a kanji run is okurigana. Use the last occurrence
            // that still fits, because a reading such as かわいい has two い for
            // a word that writes only one, as in 可愛い.
            var kanaEnd = expressionIndex
            while (kanaEnd < expression.length && !isKanji(expression[kanaEnd])) kanaEnd++
            val kanaTail = expression.substring(expressionIndex, kanaEnd)
            val readingEnd = if (kanaTail.isEmpty()) {
                reading.length
            } else {
                val lastPossible = reading.length - kanaTail.length
                if (lastPossible < readingIndex) return fallback(expression, reading)
                val at = reading.lastIndexOf(kanaTail, lastPossible)
                if (at < readingIndex) return fallback(expression, reading)
                at
            }
            if (readingEnd < readingIndex || readingEnd == readingIndex) {
                return fallback(expression, reading)
            }
            segments.add(RubySegment(kanjiRun, reading.substring(readingIndex, readingEnd)))
            readingIndex = readingEnd
        }
        if (readingIndex != reading.length) return fallback(expression, reading)
        return segments
    }

    private fun fallback(expression: String, reading: String): List<RubySegment> =
        listOf(RubySegment(expression, reading))

    /**
     * The reading of an inflected [surface], built from the dictionary form's [ruby].
     *
     * A conjugated word keeps its kanji runs, so 食べた inherits the た over 食 from
     * 食べる while the kana tail that changed stays as the surface writes it. When a
     * run cannot be found the surface itself is returned, and the caller can skip
     * ruby that would only repeat it.
     */
    fun surfaceReading(surface: String, ruby: List<RubySegment>): String {
        if (surface.isEmpty() || ruby.isEmpty()) return ""
        val out = StringBuilder(surface.length)
        var cursor = 0
        for (segment in ruby) {
            val at = surface.indexOf(segment.text, cursor)
            if (at < 0) continue
            if (at > cursor) out.append(surface, cursor, at)
            if (segment.reading != null) out.append(segment.reading) else out.append(segment.text)
            cursor = at + segment.text.length
        }
        if (cursor < surface.length) out.append(surface, cursor, surface.length)
        return out.toString()
    }

    private fun isKanji(character: Char): Boolean = when (character) {
        '\u3005', '\u3006', '\u303B', '\u3007' -> true
        in '\u3400'..'\u4DBF' -> true
        in '\u4E00'..'\u9FFF' -> true
        in '\uF900'..'\uFAFF' -> true
        in '\uD840'..'\uD87F' -> true
        else -> false
    }
}
