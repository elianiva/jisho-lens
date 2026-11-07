package com.elianiva.jisholens.dictionary

import java.text.Normalizer

/**
 * Builds and queries the FTS4 columns.
 *
 * SQLite's tokenizers treat a run of Japanese as a single word, which is
 * useless for partial input. Both indexing and querying therefore split CJK
 * characters into one token per character, so a phrase query matches any
 * substring. Latin text keeps normal word tokenization and supports prefixes.
 *
 * The tokenizer is `unicode61`, which Android's bundled SQLite provides. FTS5 is
 * not available there, so the schema sticks to FTS4.
 */
object FtsText {
    private val whitespace = Regex("\\s+")

    /**
     * Text as it goes into the FTS column. Text without Japanese is returned
     * unchanged, which is the common case for glosses and costs no allocation.
     */
    fun indexText(value: String): String {
        if (value.isEmpty()) return value
        val normalized = normalize(value)
        var index = 0
        var japanese = false
        while (index < normalized.length) {
            val codePoint = normalized.codePointAt(index)
            if (isJapanese(codePoint)) {
                japanese = true
                break
            }
            index += Character.charCount(codePoint)
        }
        if (!japanese) return normalized

        val output = StringBuilder(normalized.length + normalized.length / 2)
        var runStart = 0
        index = 0
        while (index < normalized.length) {
            val codePoint = normalized.codePointAt(index)
            if (isJapanese(codePoint)) {
                if (runStart < index) output.append(normalized, runStart, index)
                if (output.isNotEmpty() && output.last() != ' ') output.append(' ')
                output.appendCodePoint(codePoint)
                output.append(' ')
                index += Character.charCount(codePoint)
                runStart = index
            } else {
                index += Character.charCount(codePoint)
            }
        }
        if (runStart < normalized.length) output.append(normalized, runStart, normalized.length)
        return output.toString()
    }

    /** Returns an FTS4 MATCH expression, or null when the input has no searchable text. */
    fun buildQuery(input: String): String? {
        val normalized = normalize(input)
        val terms = mutableListOf<String>()
        for (token in normalized.split(whitespace)) {
            if (token.isEmpty()) continue
            terms.addAll(queryTerms(token))
        }
        if (terms.isEmpty()) return null
        return terms.joinToString(" AND ")
    }

    fun isJapanese(codePoint: Int): Boolean = when (codePoint) {
        0x3005, 0x3006, 0x303B, 0x3007 -> true
        in 0x3040..0x30FF -> true
        in 0x3400..0x4DBF -> true
        in 0x4E00..0x9FFF -> true
        in 0xF900..0xFAFF -> true
        in 0xFF66..0xFF9F -> true
        in 0x20000..0x3FFFF -> true
        else -> false
    }

    private fun normalize(value: String): String =
        if (Normalizer.isNormalized(value, Normalizer.Form.NFKC)) value else Normalizer.normalize(value, Normalizer.Form.NFKC)

    private fun queryTerms(token: String): List<String> {
        val terms = mutableListOf<String>()
        val buffer = StringBuilder()
        var bufferIsJapanese: Boolean? = null

        fun flush() {
            if (buffer.isEmpty()) return
            if (bufferIsJapanese == true) {
                val characters = mutableListOf<String>()
                var index = 0
                while (index < buffer.length) {
                    val codePoint = buffer.codePointAt(index)
                    characters.add(String(Character.toChars(codePoint)))
                    index += Character.charCount(codePoint)
                }
                terms.add("\"" + characters.joinToString(" ") + "\"")
            } else {
                val word = buffer.toString().filter { it.isLetterOrDigit() }
                if (word.isNotEmpty()) {
                    terms.add("$word*")
                }
            }
            buffer.clear()
        }

        var index = 0
        while (index < token.length) {
            val codePoint = token.codePointAt(index)
            val japanese = isJapanese(codePoint)
            if (bufferIsJapanese != null && bufferIsJapanese != japanese) {
                flush()
            }
            bufferIsJapanese = japanese
            buffer.appendCodePoint(codePoint)
            index += Character.charCount(codePoint)
        }
        flush()
        return terms
    }
}
