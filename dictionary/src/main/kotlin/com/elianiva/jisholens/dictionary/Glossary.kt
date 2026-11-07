package com.elianiva.jisholens.dictionary

import kotlinx.serialization.json.JsonElement

/**
 * Turns Yomitan glossary and meaning JSON into readable text in one pass.
 *
 * The importer runs this over raw bank text, so it works on a range of a string
 * rather than a parsed tree. Keys that carry markup or metadata (`tag`, `type`,
 * `path`, ...) are skipped; everything else contributes text.
 */
object Glossary {
    private val htmlTag = Regex("<[^>]*>")
    private val ignoredKeys = listOf("tag", "type", "style", "path", "lang", "data", "class", "href")
    private val blockTags = setOf("li", "div", "p", "tr", "td", "th", "summary", "br")

    fun text(glossary: JsonElement): String = text(glossary.toString())

    fun text(json: CharSequence, start: Int = 0, end: Int = json.length): String {
        val builder = StringBuilder(64)
        scan(json, start, end, builder)
        return collapse(builder)
    }

    private fun scan(json: CharSequence, start: Int, end: Int, output: StringBuilder) {
        val containers = ArrayList<Container>(8)
        var index = start
        while (index < end) {
            when (val character = json[index]) {
                '{', '[' -> {
                    val parent = containers.lastOrNull()
                    val suppressed = parent != null && (parent.suppressed || (parent.isObject && parent.keySuppressed))
                    containers.add(Container(character == '{', suppressed))
                    if (parent != null) {
                        parent.keySuppressed = false
                        parent.tagPending = false
                    }
                    index++
                }
                '}' -> {
                    if (containers.isNotEmpty()) {
                        val closed = containers.removeAt(containers.size - 1)
                        if (closed.tag in blockTags) appendSeparator(output)
                    }
                    index++
                }
                ']' -> {
                    if (containers.isNotEmpty()) containers.removeAt(containers.size - 1)
                    index++
                }
                ',' -> {
                    val container = containers.lastOrNull()
                    if (container != null && container.isObject) {
                        container.keySuppressed = false
                    } else if (containers.size == 1) {
                        appendSeparator(output)
                    }
                    index++
                }
                '"' -> {
                    val stringStart = index
                    index = endOfString(json, index, end)
                    if (isKey(json, index, end)) {
                        val container = containers.lastOrNull()
                        if (container != null && container.isObject) {
                            container.keySuppressed = isIgnoredKey(json, stringStart, index)
                            container.tagPending = matchesKey(json, stringStart, index, "tag")
                        }
                    } else {
                        val container = containers.lastOrNull()
                        val suppressed = container != null && (container.suppressed || (container.isObject && container.keySuppressed))
                        if (!suppressed) {
                            appendText(json, stringStart, index, output)
                        }
                        if (container != null && container.isObject) {
                            if (container.tagPending && index - stringStart <= 32) {
                                container.tag = decode(json, stringStart, index)
                            }
                            container.keySuppressed = false
                            container.tagPending = false
                        }
                    }
                }
                else -> index++
            }
        }
    }

    private fun isKey(json: CharSequence, index: Int, end: Int): Boolean {
        var position = index
        while (position < end && json[position].isWhitespace()) position++
        return position < end && json[position] == ':'
    }

    private fun isIgnoredKey(json: CharSequence, start: Int, end: Int): Boolean {
        for (key in ignoredKeys) {
            if (matchesKey(json, start, end, key)) return true
        }
        return false
    }

    /** Compares a raw string span with a literal, without decoding it. */
    private fun matchesKey(json: CharSequence, start: Int, end: Int, literal: String): Boolean {
        if (end - start - 2 != literal.length) return false
        var index = 0
        while (index < literal.length) {
            if (json[start + 1 + index] != literal[index]) return false
            index++
        }
        return true
    }

    /** Decoded value of a string span. */
    private fun decode(json: CharSequence, start: Int, end: Int): String {
        val builder = StringBuilder(end - start)
        decodeInto(json, start, end, builder)
        return builder.toString()
    }

    private fun endOfString(json: CharSequence, start: Int, end: Int): Int {
        var index = start + 1
        var escaped = false
        while (index < end) {
            val character = json[index]
            when {
                escaped -> escaped = false
                character == '\\' -> escaped = true
                character == '"' -> return index + 1
            }
            index++
        }
        return end
    }

    /** Appends the decoded string at [start, end), including its quotes. */
    private fun appendText(json: CharSequence, start: Int, end: Int, output: StringBuilder) {
        if (end - start <= 2) return
        var position = start + 1
        val last = end - 1
        var escaped = false
        var hasMarkup = false
        while (position < last) {
            val character = json[position]
            if (character == '\\') {
                escaped = true
                break
            }
            if (character == '<') hasMarkup = true
            position++
        }
        if (!escaped) {
            while (position < last) {
                if (json[position] == '<') {
                    hasMarkup = true
                    break
                }
                position++
            }
        }

        if (!hasMarkup) {
            if (!escaped) {
                output.append(json, start + 1, last)
            } else {
                decodeInto(json, start, end, output)
            }
            output.append(' ')
            return
        }

        val textBuilder = StringBuilder(last - start)
        decodeInto(json, start, end, textBuilder)
        output.append(htmlTag.replace(textBuilder, " "))
        output.append(' ')
    }

    private fun decodeInto(json: CharSequence, start: Int, end: Int, output: StringBuilder) {
        var index = start + 1
        val last = end - 1
        while (index < last) {
            val character = json[index]
            if (character == '\\' && index + 1 < last) {
                index++
                when (val escaped = json[index]) {
                    '"', '\\', '/' -> output.append(escaped)
                    'b' -> output.append('\b')
                    'f' -> output.append('\u000C')
                    'n' -> output.append('\n')
                    'r' -> output.append('\r')
                    't' -> output.append('\t')
                    'u' -> if (index + 4 < last) {
                        output.append(json.substring(index + 1, index + 5).toInt(16).toChar())
                        index += 4
                    } else {
                        output.append(escaped)
                    }
                    else -> output.append(escaped)
                }
            } else {
                output.append(character)
            }
            index++
        }
    }

    private fun appendSeparator(output: StringBuilder) {
        while (output.isNotEmpty() && output.last() == ' ') output.setLength(output.length - 1)
        if (output.isEmpty()) return
        if (output.length >= 2 && output[output.length - 1] == ';') return
        output.append("; ")
    }

    /** Collapses runs of whitespace in place and returns the trimmed text. */
    private fun collapse(builder: StringBuilder): String {
        var write = 0
        var lastWasSpace = true
        val length = builder.length
        for (read in 0 until length) {
            val character = builder[read]
            if (character == ' ' || character == '\n' || character == '\r' || character == '\t' ||
                character == '\u0000' || character == '\u000B' || character == '\u000C'
            ) {
                if (!lastWasSpace) {
                    builder.setCharAt(write++, ' ')
                    lastWasSpace = true
                }
            } else {
                builder.setCharAt(write++, character)
                lastWasSpace = false
            }
        }
        if (write > 0 && builder[write - 1] == ' ') write--
        if (write > 0 && builder[write - 1] == ';') {
            write--
            while (write > 0 && builder[write - 1] == ' ') write--
        }
        return builder.substring(0, write)
    }

    private class Container(val isObject: Boolean, val suppressed: Boolean) {
        var keySuppressed = false
        var tagPending = false
        var tag: String? = null
    }
}
