package com.elianiva.jisholens.dictionary

/**
 * A bank row viewed as column spans over the bank text.
 *
 * Yomitan bank rows are flat JSON arrays of up to eight values, and an import
 * walks hundreds of thousands of them. Building a `JsonElement` tree per row
 * allocates a map, a list, and a boxed primitive for every column. This type
 * keeps the row as offsets into the text that was already read from the zip and
 * decodes only the columns the importer needs.
 *
 * A row instance is reused: it is only valid inside the `forEachBankRow`
 * callback that produced it.
 */
class JsonRow internal constructor(val source: String) {
    private var columns = IntArray(16)

    var columnCount = 0
        private set

    fun start(index: Int): Int = columns[index * 2]

    fun end(index: Int): Int = columns[index * 2 + 1]

    fun has(index: Int): Boolean = index < columnCount

    fun isString(index: Int): Boolean =
        index < columnCount && end(index) > start(index) && source[start(index)] == '"'

    fun isArray(index: Int): Boolean =
        index < columnCount && start(index) < end(index) && source[start(index)] == '['

    fun isObject(index: Int): Boolean =
        index < columnCount && start(index) < end(index) && source[start(index)] == '{'

    /** Decoded string value, or null when the column is missing or not a string. */
    fun string(index: Int): String? {
        if (!isString(index)) return null
        val start = start(index)
        val end = end(index)
        var position = start + 1
        val last = end - 1
        while (position < last) {
            if (source[position] == '\\') return decodeEscaped(start, end)
            position++
        }
        return source.substring(start + 1, last)
    }

    fun string(index: Int, fallback: String): String = string(index) ?: fallback

    /** Raw JSON text of a column, used to store or compress nested values unchanged. */
    fun raw(index: Int): String = source.substring(start(index), end(index))

    fun number(index: Int): Double {
        if (index >= columnCount) return 0.0
        val start = start(index)
        val end = end(index)
        var position = start
        var negative = false
        if (position < end && source[position] == '-') {
            negative = true
            position++
        }
        var value = 0L
        var digits = 0
        while (position < end) {
            val character = source[position]
            if (character !in '0'..'9') {
                return source.substring(start, end).toDoubleOrNull() ?: 0.0
            }
            value = value * 10 + (character - '0')
            digits++
            position++
        }
        if (digits == 0) return 0.0
        return if (negative) -value.toDouble() else value.toDouble()
    }

    fun long(index: Int): Long? {
        if (index >= columnCount) return null
        val start = start(index)
        val end = end(index)
        var position = start
        var negative = false
        if (position < end && source[position] == '-') {
            negative = true
            position++
        }
        var value = 0L
        var digits = 0
        while (position < end) {
            val character = source[position]
            if (character !in '0'..'9') {
                return source.substring(start, end).toLongOrNull()
            }
            value = value * 10 + (character - '0')
            digits++
            position++
        }
        if (digits == 0) return null
        return if (negative) -value else value
    }

    /** Parses the columns of the row that occupies [start] until [end]. */
    internal fun load(start: Int, end: Int) {
        columnCount = 0
        var index = start + 1
        var rowEnd = end - 1
        if (rowEnd <= index) return
        while (rowEnd > index && source[rowEnd - 1].isWhitespace()) rowEnd--

        while (index < rowEnd) {
            while (index < rowEnd && (source[index].isWhitespace() || source[index] == ',')) index++
            if (index >= rowEnd) break

            val valueStart = index
            var depth = 0
            var inString = false
            var escaped = false
            while (index < rowEnd) {
                val character = source[index]
                if (inString) {
                    when {
                        escaped -> escaped = false
                        character == '\\' -> escaped = true
                        character == '"' -> inString = false
                    }
                } else {
                    when (character) {
                        '"' -> inString = true
                        '[', '{' -> depth++
                        '}' -> depth--
                        ']' -> if (depth == 0) break else depth--
                        ',' -> if (depth == 0) break
                    }
                }
                index++
            }

            var valueEnd = index
            while (valueEnd > valueStart && source[valueEnd - 1].isWhitespace()) valueEnd--
            if (valueEnd > valueStart) {
                if (columnCount * 2 + 1 >= columns.size) columns = columns.copyOf(columns.size * 2)
                columns[columnCount * 2] = valueStart
                columns[columnCount * 2 + 1] = valueEnd
                columnCount++
            }
            if (index < rowEnd) index++
        }
    }

    private fun decodeEscaped(start: Int, end: Int): String {
        val builder = StringBuilder(end - start)
        var index = start + 1
        val last = end - 1
        while (index < last) {
            val character = source[index]
            if (character == '\\' && index + 1 < last) {
                index++
                when (val escaped = source[index]) {
                    '"', '\\', '/' -> builder.append(escaped)
                    'b' -> builder.append('\b')
                    'f' -> builder.append('\u000C')
                    'n' -> builder.append('\n')
                    'r' -> builder.append('\r')
                    't' -> builder.append('\t')
                    'u' -> if (index + 4 < last) {
                        builder.append(source.substring(index + 1, index + 5).toInt(16).toChar())
                        index += 4
                    } else {
                        builder.append(escaped)
                    }
                    else -> builder.append(escaped)
                }
            } else {
                builder.append(character)
            }
            index++
        }
        return builder.toString()
    }
}
