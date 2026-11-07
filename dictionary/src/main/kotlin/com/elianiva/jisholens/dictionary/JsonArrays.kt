package com.elianiva.jisholens.dictionary

/**
 * Splits a JSON array into its top-level elements without building the whole
 * document in memory.
 *
 * A bank file is read as one string, so the scanner works on plain indexes: no
 * reader, no buffer refills, and no string per row. Each element is loaded into
 * the reused [JsonRow] and handed to [action].
 */
internal object JsonArrays {
    inline fun forEachElement(text: String, row: JsonRow, action: (JsonRow) -> Unit) {
        val length = text.length
        var index = 0
        while (index < length && text[index].isWhitespace()) index++
        if (index >= length || text[index] != '[') {
            throw YomitanFormatException("Expected a JSON array, found '${if (index < length) text[index] else ' '}'")
        }
        index++

        while (index < length) {
            while (index < length && (text[index].isWhitespace() || text[index] == ',')) index++
            if (index >= length) return
            if (text[index] == ']') return

            val start = index
            var depth = 0
            var inString = false
            var escaped = false
            var rowComplete = false

            while (index < length) {
                val character = text[index]
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
                        ']' -> if (depth == 0) {
                            row.load(start, index)
                            action(row)
                            return
                        } else {
                            depth--
                        }
                        ',' -> if (depth == 0) rowComplete = true
                    }
                }
                if (rowComplete) break
                index++
            }

            row.load(start, index)
            action(row)
            index++
        }
    }
}

class YomitanFormatException(message: String) : Exception(message)
