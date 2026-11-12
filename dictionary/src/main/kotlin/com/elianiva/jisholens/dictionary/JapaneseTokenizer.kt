package com.elianiva.jisholens.dictionary

import com.atilika.kuromoji.ipadic.Tokenizer

/**
 * One morpheme from the IPADIC model, with its range in the original text.
 *
 * Kuromoji reports the dictionary form and the part of speech, so a caller can
 * group a conjugated phrase back into one word without knowing the conjugation
 * rules. [reading] is katakana, or [UNKNOWN] when the model has no entry.
 */
data class JapaneseToken(
    val surface: String,
    val start: Int,
    val end: Int,
    val baseForm: String,
    val reading: String,
    /** Part of speech at levels 1 to 4, as IPADIC defines them. */
    val partOfSpeech: List<String>,
    /** The conjugation form, such as 基本形, or [UNKNOWN] for a word that does not inflect. */
    val conjugationForm: String,
) {
    val isInflected: Boolean get() = conjugationForm != UNKNOWN

    fun partOfSpeech(level: Int): String = partOfSpeech.getOrElse(level) { UNKNOWN }

    companion object {
        const val UNKNOWN = "*"
    }
}

/**
 * Splits Japanese text into morphemes with Kuromoji's IPADIC model.
 *
 * A character-prefix scan cannot tell 東京 + 都 from 東京都, 今日 + は from the
 * greeting 今日は, or a name from four unrelated single-kanji words, because it
 * has no cost model and no part of speech. Kuromoji carries the IPADIC
 * connection costs and the conjugation of every entry, which is the signal the
 * scan was missing.
 *
 * Kuromoji and its IPADIC data are Apache-2.0 and freely redistributable; see
 * `THIRD_PARTY_NOTICES.md`. The tokenizer loads about 36 MB of dictionary data
 * into memory once per process, so [shared] is used instead of a new instance
 * per lookup.
 *
 * A tokenizer is not thread-safe: it keeps a Viterbi builder and its cost
 * arrays. [tokenize] serializes calls on the tokenizer.
 */
class JapaneseTokenizer {
    private val tokenizer: Tokenizer? by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        runCatching { Tokenizer() }.getOrNull()
    }

    /**
     * Tokenizes [text]. If the model cannot load, every Japanese character
     * becomes a token of its own, which keeps the scan working with single
     * character entries instead of failing the screen.
     */
    fun tokenize(text: String): List<JapaneseToken> {
        if (text.isEmpty()) return emptyList()
        val tokenizer = tokenizer ?: return fallbackTokens(text)
        val tokens = synchronized(tokenizer) { tokenizer.tokenize(text) }
        val result = ArrayList<JapaneseToken>(tokens.size)
        var offset = 0
        for (token in tokens) {
            val surface = token.surface
            val start = offset
            val end = start + surface.length
            offset = end
            result.add(
                JapaneseToken(
                    surface = surface,
                    start = start,
                    end = end,
                    baseForm = token.baseForm.takeUnless { it == JapaneseToken.UNKNOWN } ?: surface,
                    reading = token.reading,
                    partOfSpeech = listOf(
                        token.partOfSpeechLevel1,
                        token.partOfSpeechLevel2,
                        token.partOfSpeechLevel3,
                        token.partOfSpeechLevel4,
                    ),
                    conjugationForm = token.conjugationForm,
                ),
            )
        }
        return result
    }

    private fun fallbackTokens(text: String): List<JapaneseToken> {
        val tokens = ArrayList<JapaneseToken>(text.length)
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            val width = Character.charCount(codePoint)
            if (FtsText.isJapanese(codePoint)) {
                val surface = text.substring(index, index + width)
                tokens.add(
                    JapaneseToken(
                        surface = surface,
                        start = index,
                        end = index + width,
                        baseForm = surface,
                        reading = JapaneseToken.UNKNOWN,
                        partOfSpeech = listOf(JapaneseToken.UNKNOWN),
                        conjugationForm = JapaneseToken.UNKNOWN,
                    ),
                )
            }
            index += width
        }
        return tokens
    }

    companion object {
        val shared: JapaneseTokenizer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { JapaneseTokenizer() }
    }
}
