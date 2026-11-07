package com.elianiva.jisholens.dictionary

/**
 * Kana normalization used by lookup. Japanese readings are stored as they come
 * from the dictionary (usually hiragana), so a katakana query has to be folded
 * to hiragana before it can match.
 */
object Kana {
    fun toHiragana(text: String): String = StringBuilder(text.length).apply {
        for (character in text) {
            append(hiraganaFor(character))
        }
    }.toString()

    fun toKatakana(text: String): String = StringBuilder(text.length).apply {
        for (character in text) {
            append(katakanaFor(character))
        }
    }.toString()

    private fun hiraganaFor(character: Char): Char = when (character) {
        in '\u30A1'..'\u30F6' -> character - 0x60
        '\u30FD' -> '\u309D'
        '\u30FE' -> '\u309E'
        else -> character
    }

    private fun katakanaFor(character: Char): Char = when (character) {
        in '\u3041'..'\u3096' -> character + 0x60
        '\u309D' -> '\u30FD'
        '\u309E' -> '\u30FE'
        else -> character
    }
}
