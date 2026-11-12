package com.elianiva.jisholens.dictionary

/** A word-sized range of the scanned text, with the reading the tokenizer assigned to it. */
internal data class ScanSpan(
    val start: Int,
    val end: Int,
    val surface: String,
    /** Katakana reading of the whole span, or null when a token has none. */
    val reading: String?,
    /**
     * Dictionary form of the first content token, when the span inflects it.
     * 静かだった carries 静か here, so the word resolves even when the rule
     * chain cannot strip the copula.
     */
    val baseForm: String? = null,
)

/** Tells the segmenter which spans the dictionary can answer. */
internal interface ScanLexicon {
    /** True when [surface] is a headword itself or deinflects to one. */
    fun resolves(surface: String): Boolean

    /** The readings of the exact headwords for [surface], folded to hiragana. */
    fun readings(surface: String): Set<String>
}

/**
 * Groups the tokens of a line into words a reader would tap.
 *
 * The tokenizer decides the boundaries; this class only joins tokens back into
 * words that the dictionary knows. Two joins matter:
 *
 *  * a conjugation chain, such as 食べ + させ + られ + た, becomes one word so
 *    the card can deinflect the whole phrase;
 *  * adjacent nouns and affixes, such as 東京 + 都 or 予算 + 案, become the
 *    compound headword, but only when the compound's reading matches the
 *    tokens' readings. That guard is what stops 今日 + は ("today" + topic
 *    particle) from becoming the greeting 今日は.
 *
 * A word is kept only when the dictionary resolves it. An unknown span is
 * dropped, so it never swallows the words on either side of it.
 */
internal class ScanSegmenter {
    fun groups(
        text: String,
        tokens: List<JapaneseToken>,
        lexicon: ScanLexicon,
    ): List<TokenGroup> {
        val groups = ArrayList<TokenGroup>()
        var index = 0
        while (index < tokens.size) {
            val token = tokens[index]
            if (!token.isJapanese()) {
                index++
                continue
            }
            val chosen = chainGroup(text, tokens, index, lexicon)
                ?: compoundGroup(text, tokens, index, lexicon)
                ?: group(text, tokens, index, index + 1)
            if (lexicon.resolves(chosen.span.surface)) groups.add(chosen)
            index = chosen.endToken
        }
        return groups
    }

    /**
     * Every word that could answer a tap at [offset]. This includes the token
     * under the tap, its conjugation chain, noun compounds that contain the tap,
     * and every dictionary-sized prefix that starts at the tapped character. The
     * caller resolves the surfaces, drops the ones the dictionary does not know,
     * and ranks what remains.
     */
    fun tapSpans(
        text: String,
        tokens: List<JapaneseToken>,
        offset: Int,
        maxLength: Int,
    ): List<TapSpan> {
        if (offset !in text.indices) return emptyList()
        val spans = LinkedHashMap<Long, TapSpan>()

        fun add(candidate: ScanSpan, aligned: Boolean, chain: Boolean) {
            if (candidate.start > offset || candidate.end <= offset) return
            val key = rangeKey(candidate.start, candidate.end)
            val existing = spans[key]
            spans[key] = if (existing == null) {
                TapSpan(candidate, aligned, chain)
            } else {
                existing.copy(
                    aligned = existing.aligned || aligned,
                    chain = existing.chain || chain,
                )
            }
        }

        val tokenIndex = tokens.indexOfFirst { offset >= it.start && offset < it.end }
        if (tokenIndex >= 0) {
            val token = tokens[tokenIndex]
            add(
                ScanSpan(
                    start = token.start,
                    end = token.end,
                    surface = token.surface,
                    reading = token.readingOrNull(),
                    baseForm = token.baseForm.takeIf { it != token.surface },
                ),
                aligned = true,
                chain = token.isInflected,
            )
        }

        val window = if (tokenIndex < 0) {
            IntRange.EMPTY
        } else {
            maxOf(0, tokenIndex - MAX_MERGE_TOKENS)..tokenIndex
        }
        for (index in window) {
            val token = tokens[index]
            if (!token.isJapanese()) continue

            val chainStart = startsChain(tokens, index)
            if (chainStart >= 0) {
                val chainEnd = chainEnd(tokens, chainStart)
                for (end in chainStart + 2..chainEnd) {
                    val candidate = span(text, tokens, chainStart, end)
                    add(candidate, aligned = true, chain = true)
                }
            }

            val last = minOf(tokens.size, index + MAX_MERGE_TOKENS)
            for (end in index + 2..last) {
                if (!canMergeNouns(tokens, index, end)) break
                add(span(text, tokens, index, end), aligned = true, chain = false)
            }
        }

        var end = offset
        var length = 0
        while (end < text.length && length < maxLength) {
            val codePoint = text.codePointAt(end)
            if (!FtsText.isJapanese(codePoint)) break
            end += Character.charCount(codePoint)
            length++
            add(
                ScanSpan(offset, end, text.substring(offset, end), readingFor(tokens, offset, end)),
                aligned = false,
                chain = false,
            )
        }

        return spans.values.toList()
    }

    /**
     * All spans of up to [MAX_MERGE_TOKENS] whole tokens. They are the exact
     * headwords the tiling considers; querying them in one batch keeps a line
     * to a single database round trip.
     */
    fun exactSpans(text: String, tokens: List<JapaneseToken>): List<ScanSpan> {
        val spans = ArrayList<ScanSpan>(tokens.size * 2)
        for (start in tokens.indices) {
            if (!tokens[start].isJapanese()) continue
            val last = minOf(tokens.size, start + MAX_MERGE_TOKENS)
            for (end in start + 1..last) {
                if (end > start + 1 && !canMergeNouns(tokens, start, end)) break
                spans.add(span(text, tokens, start, end))
            }
        }
        return spans
    }

    /** The spans whose surface is a conjugation chain, and so is worth deinflecting. */
    fun chainSpans(text: String, tokens: List<JapaneseToken>): List<ScanSpan> {
        val spans = ArrayList<ScanSpan>(tokens.size)
        for (start in tokens.indices) {
            if (!tokens[start].isJapanese()) continue
            val chainStart = startsChain(tokens, start)
            if (chainStart >= 0) {
                val end = chainEnd(tokens, chainStart)
                if (end > chainStart + 1) spans.add(span(text, tokens, chainStart, end))
            }
            val token = tokens[start]
            if (token.isInflected) spans.add(span(text, tokens, start, start + 1))
        }
        return spans
    }

    private fun chainGroup(
        text: String,
        tokens: List<JapaneseToken>,
        index: Int,
        lexicon: ScanLexicon,
    ): TokenGroup? {
        val start = startsChain(tokens, index)
        if (start < 0) return null
        val end = chainEnd(tokens, start)
        for (last in end downTo start + 2) {
            val candidate = span(text, tokens, start, last)
            if (lexicon.resolves(candidate.surface)) return group(text, tokens, start, last)
        }
        return null
    }

    private fun compoundGroup(
        text: String,
        tokens: List<JapaneseToken>,
        index: Int,
        lexicon: ScanLexicon,
    ): TokenGroup? {
        val last = minOf(tokens.size, index + MAX_MERGE_TOKENS)
        for (end in last downTo index + 2) {
            if (!canMergeNouns(tokens, index, end)) continue
            val candidate = span(text, tokens, index, end)
            if (lexicon.readings(candidate.surface).contains(hiragana(candidate.reading))) {
                return group(text, tokens, index, end)
            }
        }
        return null
    }

    private fun group(text: String, tokens: List<JapaneseToken>, start: Int, endToken: Int): TokenGroup =
        TokenGroup(
            start = tokens[start].start,
            end = tokens[endToken - 1].end,
            endToken = endToken,
            span = span(text, tokens, start, endToken),
        )

    private fun span(text: String, tokens: List<JapaneseToken>, start: Int, endToken: Int): ScanSpan {
        val begin = tokens[start].start
        val finish = tokens[endToken - 1].end
        val surface = text.substring(begin, finish)
        return ScanSpan(
            start = begin,
            end = finish,
            surface = surface,
            reading = readingFor(tokens, begin, finish),
            baseForm = tokens[start].baseForm.takeIf { it != surface },
        )
    }

    private fun readingFor(tokens: List<JapaneseToken>, start: Int, end: Int): String? {
        val readings = StringBuilder()
        var covered = start
        for (token in tokens) {
            if (token.end <= start || token.start >= end) continue
            if (token.start != covered) return null
            val reading = token.readingOrNull() ?: return null
            readings.append(reading)
            covered = token.end
        }
        return readings.takeIf { covered == end }?.toString()
    }

    private fun startsChain(tokens: List<JapaneseToken>, index: Int): Int {
        val token = tokens[index]
        return when {
            token.partOfSpeech(0) == "動詞" && token.partOfSpeech(1) == "自立" -> index
            token.partOfSpeech(0) == "形容詞" && token.partOfSpeech(1) == "自立" -> index
            token.partOfSpeech(0) == "名詞" && token.partOfSpeech(1) == "サ変接続" -> {
                if (tokens.getOrNull(index + 1)?.let(::isSuru) == true) index else -1
            }
            token.partOfSpeech(0) == "名詞" && token.partOfSpeech(1) == "形容動詞語幹" -> {
                if (tokens.getOrNull(index + 1)?.let(::isCopula) == true) index else -1
            }
            // An auxiliary after a noun, such as 雨だった, has no content token
            // to start from; grouping it keeps the copula in one word.
            token.partOfSpeech(0) == "助動詞" -> index
            else -> -1
        }
    }

    /** The exclusive index after the last token that belongs to the chain at [start]. */
    private fun chainEnd(tokens: List<JapaneseToken>, start: Int): Int {
        var end = start + 1
        while (end < tokens.size) {
            val token = tokens[end]
            val previous = tokens[end - 1]
            if (!token.isJapanese() || token.start != previous.end) break
            if (!continuesChain(tokens, start, end)) break
            end++
        }
        return end
    }

    private fun continuesChain(tokens: List<JapaneseToken>, start: Int, index: Int): Boolean {
        val token = tokens[index]
        if (isSuru(token) && tokens[start].partOfSpeech(1) == "サ変接続") return true
        if (isCopula(token) && tokens[start].partOfSpeech(1) == "形容動詞語幹") return true
        return when (token.partOfSpeech(0)) {
            "助動詞" -> token.baseForm != "らしい"
            "助詞" -> token.partOfSpeech(1) == "接続助詞"
            "動詞" -> token.partOfSpeech(1) == "接尾" || token.partOfSpeech(1) == "非自立"
            "形容詞" -> token.partOfSpeech(1) == "非自立"
            else -> false
        }
    }

    private fun canMergeNouns(tokens: List<JapaneseToken>, start: Int, end: Int): Boolean {
        if (end - start > MAX_MERGE_TOKENS) return false
        for (index in start until end) {
            val token = tokens[index]
            if (!token.isJapanese()) return false
            if (index > start && token.start != tokens[index - 1].end) return false
            if (token.partOfSpeech(0) != "名詞" && token.partOfSpeech(0) != "接頭詞") return false
        }
        return tokens[start].partOfSpeech(0) == "名詞" || tokens[start].partOfSpeech(0) == "接頭詞"
    }

    private fun isSuru(token: JapaneseToken): Boolean =
        token.partOfSpeech(0) == "動詞" && token.baseForm == "する"

    private fun isCopula(token: JapaneseToken): Boolean =
        token.partOfSpeech(0) == "助動詞" && (token.baseForm == "だ" || token.baseForm == "です")

    private fun hiragana(reading: String?): String? = reading?.let(Kana::toHiragana)

    private fun rangeKey(start: Int, end: Int): Long = start.toLong() shl 32 or end.toLong()

    private fun JapaneseToken.isJapanese(): Boolean =
        surface.isNotEmpty() && FtsText.isJapanese(surface.codePointAt(0))

    private fun JapaneseToken.readingOrNull(): String? =
        reading.takeUnless { it == JapaneseToken.UNKNOWN || it.isEmpty() }

    /** A candidate for a tap, with the evidence that decides where it ranks. */
    data class TapSpan(
        val span: ScanSpan,
        /** True when the tokenizer produced this exact boundary. */
        val aligned: Boolean,
        /** True when the span is a conjugation chain and has a base form to seed. */
        val chain: Boolean,
    )

    /** A word of the tiling, with the token index where the next word starts. */
    data class TokenGroup(
        val start: Int,
        val end: Int,
        val endToken: Int,
        val span: ScanSpan,
    )

    private companion object {
        /** 東京 + 都, 予算 + 案: at most this many tokens in one compound. */
        const val MAX_MERGE_TOKENS = 6
    }
}
