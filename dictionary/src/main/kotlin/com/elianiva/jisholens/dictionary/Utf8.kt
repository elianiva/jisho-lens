package com.elianiva.jisholens.dictionary

/** Encodes a string range as UTF-8 straight into a byte buffer. */
internal object Utf8 {
    /** Worst-case number of bytes for [length] characters. */
    fun maxBytes(length: Int): Int = length * 3

    fun encode(text: String, start: Int, end: Int, output: ByteArray, offset: Int): Int {
        var position = start
        var write = offset
        while (position < end) {
            val character = text[position]
            val code = character.code
            when {
                code < 0x80 -> output[write++] = code.toByte()
                code < 0x800 -> {
                    output[write++] = (0xC0 or (code shr 6)).toByte()
                    output[write++] = (0x80 or (code and 0x3F)).toByte()
                }
                Character.isHighSurrogate(character) && position + 1 < end && Character.isLowSurrogate(text[position + 1]) -> {
                    val codePoint = Character.toCodePoint(character, text[position + 1])
                    position++
                    output[write++] = (0xF0 or (codePoint shr 18)).toByte()
                    output[write++] = (0x80 or ((codePoint shr 12) and 0x3F)).toByte()
                    output[write++] = (0x80 or ((codePoint shr 6) and 0x3F)).toByte()
                    output[write++] = (0x80 or (codePoint and 0x3F)).toByte()
                }
                else -> {
                    output[write++] = (0xE0 or (code shr 12)).toByte()
                    output[write++] = (0x80 or ((code shr 6) and 0x3F)).toByte()
                    output[write++] = (0x80 or (code and 0x3F)).toByte()
                }
            }
            position++
        }
        return write - offset
    }
}
