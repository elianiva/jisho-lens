package com.elianiva.jisholens.dictionary

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** Keeps both IN lists of a chunk below SQLite's bound-variable limit. */
private const val QUERY_CHUNK = 400

/** One term row with the glossary decoded from its compressed block. */
data class TermResult(
    val termId: Long,
    val dictionaryId: Long,
    val dictionaryTitle: String,
    val expression: String,
    val reading: String,
    val definitionTags: String,
    val termTags: String,
    val rules: String,
    val score: Double,
    val sequence: Long?,
    val glossary: JsonArray,
    /** How the query was deinflected to this entry, or null for an exact match. */
    val deinflection: Deinflection? = null,
) {
    val glossaryText: String get() = Glossary.text(glossary)

    /**
     * Every part-of-speech tag this entry carries.
     *
     * The `rules` and `definitionTags` columns hold them for JMdict, but
     * Jitendex keeps the tags in the glossary's structured content instead, so
     * a suru noun such as 開催 had no verb class and never matched 開催される.
     * Reading the codes from the glossary fixes imported Jitendex databases
     * without a re-import.
     */
    val partsOfSpeech: Set<String> by lazy(LazyThreadSafetyMode.NONE) {
        val columns = buildSet {
            rules.split(' ').filterTo(this) { it.isNotEmpty() }
            definitionTags.split(' ').filterTo(this) { it.isNotEmpty() }
        }
        // A verb or i-adjective carries its class in the columns. Only a suru
        // noun or a na-adjective needs the glossary walk, which is the costly
        // part on a real Jitendex glossary.
        if (columns.any(DeinflectionTags::isPartOfSpeechTag)) {
            columns
        } else {
            columns + Glossary.partOfSpeechTags(glossary)
        }
    }
}

/** A word found by [DictionaryLookup.scan] or [DictionaryLookup.scanAt], with its range in the text. */
data class ScanMatch(
    val surface: String,
    val start: Int,
    val end: Int,
    val results: List<TermResult>,
    /** True when the surface itself is a headword, not a conjugated form. */
    val exact: Boolean = false,
    /**
     * Position in the tap candidate list, where 0 is the best answer. The app
     * resolves frequency metadata, and sorts by this first so a frequency rank
     * cannot push a fragment above the word that was tapped.
     */
    val priority: Int = 0,
)

/** A tap candidate, with the tokenizer evidence that decides where it ranks. */
private class TapCandidate(
    val span: ScanSpan,
    val results: List<TermResult>,
    val exact: Boolean,
    val aligned: Boolean,
    val readingMatch: Boolean,
) {
    val length: Int get() = span.end - span.start
}

/** One kanji row. */
data class KanjiResult(
    val kanjiId: Long,
    val dictionaryId: Long,
    val dictionaryTitle: String,
    val character: String,
    val onyomi: String,
    val kunyomi: String,
    val tags: String,
    val meanings: JsonArray,
    val stats: JsonObject,
) {
    val meaningsText: String get() = Glossary.text(meanings)
}

/**
 * Reads the dictionary. [lookup] answers "what is this word", and [search]
 * answers "which entries contain this text", using the FTS4 tables.
 *
 * Both accept conjugated input. An exact match comes first; when the input is
 * not a dictionary entry, [Deinflector] finds the dictionary forms that the
 * conjugation rules can reach.
 */
class DictionaryLookup(private val database: SqliteDatabase) {
    private val deinflector = Deinflector.default
    private val tokenizer = JapaneseTokenizer.shared
    private val segmenter = ScanSegmenter()

    fun lookup(text: String, limit: Int = 32): List<TermResult> {
        val value = text.trim()
        if (value.isEmpty() || limit <= 0) return emptyList()
        val exact = queryExact(value, limit)
        if (exact.size >= limit) return exact
        val deinflections = deinflector.deinflect(value)
            .filter { DeinflectionTags.isDictionaryForm(it.tags) }
        if (deinflections.isEmpty()) return exact.ifEmpty { lookupParts(value, limit) }
        val candidates = queryDeinflected(deinflections, limit)
        if (exact.isEmpty()) return candidates.ifEmpty { lookupParts(value, limit) }
        val seen = exact.mapTo(HashSet(exact.size)) { it.termId }
        return (exact + candidates.filter { seen.add(it.termId) }).take(limit)
    }

    /**
     * Answers a text that is longer than any headword, such as a selected
     * 渋谷区 or a copied phrase, with the entries of the words inside it. The
     * scan already segments the text, so a reader can at least reach 渋谷.
     */
    private fun lookupParts(value: String, limit: Int): List<TermResult> {
        val seen = HashSet<Long>()
        val results = ArrayList<TermResult>()
        for (match in scan(value, limitPerWord = limit)) {
            for (term in match.results) {
                if (results.size >= limit) return results
                if (seen.add(term.termId)) results.add(term)
            }
        }
        return results
    }

    /**
     * Finds the words in [text] that a reader would tap.
     *
     * The line is tokenized with Kuromoji, and the tokens are joined back into
     * dictionary words: a conjugation chain such as 食べ + させ + られ + た
     * becomes one word, and adjacent nouns become a compound such as 東京都 or
     * 予算案 only when the dictionary has that compound with the same reading.
     * Every word is then looked up exactly and through the deinflector in one
     * batch, and a word that the dictionary cannot resolve is left out so it
     * cannot hide its neighbours.
     *
     * [maxLength] bounds the character-prefix candidates of [scanAt]. The
     * tokenizer decides whole words here, so a long name is not cut short.
     */
    fun scan(text: String, maxLength: Int = 12, limitPerWord: Int = 6): List<ScanMatch> {
        if (text.isEmpty() || limitPerWord <= 0) return emptyList()
        val tokens = tokenizer.tokenize(text)
        if (tokens.isEmpty()) return emptyList()
        val exactSpans = segmenter.exactSpans(text, tokens)
        val chainSpans = segmenter.chainSpans(text, tokens)
        val lexicon = resolve(
            exactSurfaces = exactSpans.map { it.surface },
            deinflectSurfaces = chainSpans.map { it.surface },
            seeds = seeds(chainSpans),
            limitPerWord = limitPerWord,
        )
        val matches = ArrayList<ScanMatch>()
        for (group in segmenter.groups(text, tokens, lexicon)) {
            val results = lexicon.results(group.span.surface, group.span.reading, limitPerWord)
            if (results.isEmpty()) continue
            matches.add(
                ScanMatch(
                    surface = group.span.surface,
                    start = group.span.start,
                    end = group.span.end,
                    results = results,
                    exact = lexicon.isExact(group.span.surface),
                ),
            )
        }
        return matches
    }

    /**
     * Collects the words that could answer a tap at [offset], best first.
     *
     * A tap is ambiguous: in 東京都 the reader may mean 東京都, 東京, or 京都, and
     * in 開けた the conjugated word is a better answer than the noun 開け that
     * happens to be a shorter headword. The candidates are therefore not the
     * longest prefixes alone. They are:
     *
     *  * the segmented word under the tap, and any conjugation chain that
     *    contains it;
     *  * compound headwords that contain the tap;
     *  * every dictionary-sized prefix that starts at the tapped character, so
     *    a surface longer than any headword still offers its parts.
     *
     * They are ordered by how well they match the tap: a span that starts at the
     * tapped character beats one that starts earlier (tap 天気 in いい天気), a
     * tokenizer-aligned word beats a bare prefix (開けた before 開け), a reading
     * that matches the tokenizer beats a homograph (今日 before 今日は), and a
     * longer span wins the remaining ties. [priority] carries that order to the
     * caller; the entries inside a candidate lead with the dictionary form that
     * covers the tapped surface.
     */
    fun scanAt(text: String, offset: Int, maxLength: Int = 12, limit: Int = 8): List<ScanMatch> {
        if (offset !in text.indices || maxLength <= 0 || limit <= 0) return emptyList()
        if (!FtsText.isJapanese(text.codePointAt(offset))) return emptyList()

        val tokens = tokenizer.tokenize(text)
        if (tokens.isEmpty()) return emptyList()
        val spans = segmenter.tapSpans(text, tokens, offset, maxLength)
        if (spans.isEmpty()) return emptyList()
        val chains = spans.filter { it.chain }.map { it.span }
        val prefixes = spans.filterNot { it.aligned || it.chain }.map { it.span }
        val lexicon = resolve(
            exactSurfaces = spans.map { it.span.surface },
            deinflectSurfaces = chains.map { it.surface } + prefixes.map { it.surface },
            seeds = seeds(chains),
            limitPerWord = limit,
        )

        val candidates = ArrayList<TapCandidate>(spans.size)
        for (candidate in spans) {
            val span = candidate.span
            val results = lexicon.results(span.surface, span.reading, limit)
            if (results.isEmpty()) continue
            val wanted = span.reading?.let(Kana::toHiragana)
            candidates.add(
                TapCandidate(
                    span = span,
                    results = results,
                    exact = lexicon.isExact(span.surface),
                    aligned = candidate.aligned,
                    readingMatch = wanted != null && wanted in lexicon.readings(span.surface),
                ),
            )
        }

        val ranked = candidates.sortedWith(
            compareBy<TapCandidate> { if (it.span.start == offset) 0 else 1 }
                .thenBy { if (it.aligned) 0 else 1 }
                .thenByDescending { it.length }
                .thenByDescending { it.readingMatch }
                .thenByDescending { it.exact }
                .thenByDescending { it.results.firstOrNull()?.score ?: 0.0 },
        )
        return compressStems(ranked)
            .take(limit)
            .mapIndexed { index, candidate ->
                ScanMatch(
                    surface = candidate.span.surface,
                    start = candidate.span.start,
                    end = candidate.span.end,
                    results = candidate.results,
                    exact = candidate.exact,
                    priority = index,
                )
            }
    }

    /** Drops alternatives that only repeat entries an earlier, better candidate already offers. */
    private fun compressStems(candidates: List<TapCandidate>): List<TapCandidate> {
        val offered = HashSet<Long>()
        val kept = ArrayList<TapCandidate>(candidates.size)
        for (candidate in candidates) {
            val ids = candidate.results.mapTo(HashSet(candidate.results.size)) { it.termId }
            val addsSomething = candidate.exact || !offered.containsAll(ids)
            if (!addsSomething) continue
            offered.addAll(ids)
            kept.add(candidate)
        }
        return kept
    }

    fun search(query: String, limit: Int = 50): List<TermResult> {
        val value = query.trim()
        if (value.isEmpty() || limit <= 0) return emptyList()
        val match = FtsText.buildQuery(value) ?: return emptyList()
        val exact = queryFts(match, limit)
        if (exact.size >= limit) return exact
        val deinflections = deinflector.deinflect(value)
            .filter { DeinflectionTags.isDictionaryForm(it.tags) }
        if (deinflections.isEmpty()) return exact

        val seen = exact.mapTo(HashSet(exact.size)) { it.termId }
        val extra = ArrayList<TermResult>()
        val formsByText = LinkedHashMap<String, MutableList<Deinflection>>()
        for (form in deinflections) {
            formsByText.getOrPut(form.text) { ArrayList() }.add(form)
        }
        for ((formText, forms) in formsByText) {
            if (exact.size + extra.size >= limit) break
            val formQuery = FtsText.buildQuery(formText) ?: continue
            for (term in queryFts(formQuery, limit)) {
                if (term.termId in seen) continue
                val form = forms.firstOrNull {
                    DeinflectionTags.matches(term.partsOfSpeech, it.tags)
                } ?: continue
                seen.add(term.termId)
                extra.add(term.copy(deinflection = form))
                if (exact.size + extra.size >= limit) break
            }
        }
        return exact + extra
    }

    private fun queryExact(value: String, limit: Int): List<TermResult> {
        val candidates = linkedSetOf(value, Kana.toHiragana(value), Kana.toKatakana(value)).toList()
        val placeholders = candidates.joinToString(", ") { "?" }
        return termResults(
            database.query(
                """
                SELECT t.id, t.dictionaryId, d.title, t.expression, t.reading, t.definitionTags, t.termTags,
                       t.rules, t.score, t.sequence, t.glossaryBlock, t.glossaryOffset, t.glossaryLength
                FROM terms t
                JOIN dictionaries d ON d.id = t.dictionaryId
                WHERE d.enabled = 1
                  AND (t.expression IN ($placeholders) OR t.reading IN ($placeholders))
                ORDER BY d.priority ASC, t.score DESC, LENGTH(t.expression) ASC
                LIMIT ?
                """,
                buildList {
                    addAll(candidates)
                    addAll(candidates)
                    add(limit)
                },
            ),
        )
    }

    /** Exact entries first, then deinflected ones, without repeating a term. */
    private fun mergeResults(
        exact: List<TermResult>?,
        deinflected: List<TermResult>?,
        limit: Int,
    ): List<TermResult> {
        val results = ArrayList<TermResult>(limit)
        val seen = HashSet<Long>()
        exact.orEmpty().forEach { term ->
            if (seen.add(term.termId)) results.add(term)
        }
        deinflected.orEmpty().forEach { term ->
            if (results.size < limit && seen.add(term.termId)) results.add(term)
        }
        return results
    }

    /** The dictionary form each chain reaches, keyed by the chain's surface. */
    private fun seeds(spans: Collection<ScanSpan>): Map<String, String> = buildMap {
        for (span in spans) {
            val base = span.baseForm ?: continue
            if (base != span.surface) put(span.surface, base)
        }
    }

    /**
     * Resolves every span of a line in two batches: one exact query for all
     * token-aligned spans, and one deinflection query for the conjugation
     * chains. The result tells the segmenter which spans are words and which
     * are only fragments.
     */
    private fun resolve(
        exactSurfaces: Collection<String>,
        deinflectSurfaces: Collection<String>,
        seeds: Map<String, String>,
        limitPerWord: Int,
    ): ResolvedLexicon {
        val exact = exactCandidateResults(exactSurfaces.toSet(), limitPerWord)
        val baseTerms = if (seeds.isEmpty()) {
            emptyMap()
        } else {
            exactCandidateResults(seeds.values.toSet(), limitPerWord)
        }
        val seedTerms = seeds.mapValues { (_, base) -> baseTerms[base].orEmpty() }
            .filterValues { it.isNotEmpty() }
        val formsBySurface = LinkedHashMap<String, List<Deinflection>>()
        for (surface in deinflectSurfaces.toSet()) {
            if (surface.isEmpty()) continue
            val forms = deinflector.deinflect(surface)
                .filter { DeinflectionTags.isDictionaryForm(it.tags) }
            if (forms.isNotEmpty()) formsBySurface[surface] = forms
        }
        val deinflected = if (formsBySurface.isEmpty()) {
            emptyMap()
        } else {
            deinflectedCandidateResults(formsBySurface, limitPerWord)
        }
        return ResolvedLexicon(exact, deinflected, seedTerms)
    }

    private inner class ResolvedLexicon(
        private val exact: Map<String, List<TermResult>>,
        private val deinflected: Map<String, List<TermResult>>,
        private val seeds: Map<String, List<TermResult>>,
    ) : ScanLexicon {
        override fun resolves(surface: String): Boolean =
            exact[surface].orEmpty().isNotEmpty() ||
                deinflected[surface].orEmpty().isNotEmpty() ||
                seeds[surface].orEmpty().isNotEmpty()

        override fun readings(surface: String): Set<String> =
            exact[surface].orEmpty().mapTo(HashSet()) { Kana.toHiragana(it.reading) }

        fun isExact(surface: String): Boolean = exact.containsKey(surface)

        /**
         * The entries for a word. A lemma that covers the whole surface comes
         * first (分かりやすい before わかる, 開ける before 開け), then the entries
         * of the surface itself. Entries whose reading the tokenizer agrees on
         * lead among the exact ones.
         */
        fun results(surface: String, reading: String?, limit: Int): List<TermResult> {
            val wanted = reading?.let(Kana::toHiragana)
            val lemmas = ArrayList<Pair<TermResult, Int>>()
            // Deinflected entries carry the rule chain, so they lead a tie with
            // the seed entry that reaches the same base form.
            deinflected[surface].orEmpty().forEach { term ->
                lemmas.add(term to (term.deinflection?.text?.length ?: 0))
            }
            seeds[surface].orEmpty().forEach { lemmas.add(it to it.expression.length) }
            lemmas.sortWith(
                compareByDescending<Pair<TermResult, Int>> { it.second }
                    .thenByDescending { it.first.score },
            )
            val exactResults = if (wanted == null) {
                exact[surface].orEmpty()
            } else {
                exact[surface].orEmpty().sortedByDescending { Kana.toHiragana(it.reading) == wanted }
            }
            val results = ArrayList<TermResult>(limit)
            val seen = HashSet<Long>(limit)
            for ((term, _) in lemmas) {
                if (results.size >= limit) return results
                if (seen.add(term.termId)) results.add(term)
            }
            for (term in exactResults) {
                if (results.size >= limit) break
                if (seen.add(term.termId)) results.add(term)
            }
            return results
        }
    }

    private fun exactCandidateResults(
        candidates: Collection<String>,
        limitPerWord: Int,
    ): Map<String, List<TermResult>> {
        val byHiragana = HashMap<String, MutableList<String>>(candidates.size)
        val values = LinkedHashSet<String>(candidates.size * 3)
        for (candidate in candidates) {
            byHiragana.getOrPut(Kana.toHiragana(candidate)) { ArrayList() }.add(candidate)
            // A katakana surface such as リンゴ has to reach the kanji headword
            // 林檎 whose reading is りんご.
            values.add(candidate)
            values.add(Kana.toHiragana(candidate))
            values.add(Kana.toKatakana(candidate))
        }
        val result = HashMap<String, MutableList<TermResult>>()
        for (term in queryTermsForValues(values, limitPerWord)) {
            val keys = setOf(Kana.toHiragana(term.expression), Kana.toHiragana(term.reading))
            for (key in keys) {
                for (candidate in byHiragana[key].orEmpty()) {
                    val entries = result.getOrPut(candidate) { ArrayList() }
                    if (entries.none { it.termId == term.termId }) entries.add(term)
                }
            }
        }
        return result
    }

    private fun deinflectedCandidateResults(
        candidates: Collection<String>,
        limitPerWord: Int,
    ): Map<String, List<TermResult>> {
        val formsByCandidate = LinkedHashMap<String, List<Deinflection>>()
        for (candidate in candidates) {
            val forms = deinflector.deinflect(candidate)
                .filter { DeinflectionTags.isDictionaryForm(it.tags) }
            if (forms.isNotEmpty()) formsByCandidate[candidate] = forms
        }
        return deinflectedCandidateResults(formsByCandidate, limitPerWord)
    }

    private fun deinflectedCandidateResults(
        formsByCandidate: Map<String, List<Deinflection>>,
        limitPerWord: Int,
    ): Map<String, List<TermResult>> {
        val values = LinkedHashSet<String>()
        for (forms in formsByCandidate.values) {
            for (form in forms) {
                values.add(form.text)
                values.add(Kana.toHiragana(form.text))
                values.add(Kana.toKatakana(form.text))
            }
        }
        if (values.isEmpty()) return emptyMap()

        val terms = queryTermsForValues(values, limitPerWord)
        val result = HashMap<String, MutableList<TermResult>>()
        for ((candidate, forms) in formsByCandidate) {
            val entries = ArrayList<TermResult>(2)
            for (term in terms) {
                val form = forms.firstOrNull { candidateForm ->
                    (Kana.toHiragana(candidateForm.text) == Kana.toHiragana(term.expression) ||
                        Kana.toHiragana(candidateForm.text) == Kana.toHiragana(term.reading)) &&
                        DeinflectionTags.matches(term.partsOfSpeech, candidateForm.tags)
                } ?: continue
                if (entries.none { it.termId == term.termId }) {
                    entries.add(term.copy(deinflection = form))
                }
                if (entries.size >= limitPerWord) break
            }
            if (entries.isNotEmpty()) result[candidate] = entries
        }
        return result
    }

    /** One query per chunk for a set of expression or reading values. */
    private fun queryTermsForValues(values: Collection<String>, limitPerWord: Int): List<TermResult> {
        if (values.isEmpty()) return emptyList()
        val limit = (values.size * limitPerWord).coerceAtLeast(64)
        val result = LinkedHashMap<Long, TermResult>()
        for (chunk in values.chunked(QUERY_CHUNK)) {
            val placeholders = chunk.joinToString(", ") { "?" }
            val rows = database.query(
                """
                SELECT t.id, t.dictionaryId, d.title, t.expression, t.reading, t.definitionTags, t.termTags,
                       t.rules, t.score, t.sequence, t.glossaryBlock, t.glossaryOffset, t.glossaryLength
                FROM terms t
                JOIN dictionaries d ON d.id = t.dictionaryId
                WHERE d.enabled = 1
                  AND (t.expression IN ($placeholders) OR t.reading IN ($placeholders))
                ORDER BY d.priority ASC, t.score DESC, LENGTH(t.expression) ASC
                LIMIT ?
                """,
                chunk + chunk + listOf(limit),
            )
            termResults(rows).forEach { term -> result.putIfAbsent(term.termId, term) }
        }
        return result.values.toList()
    }

    private fun queryDeinflected(forms: List<Deinflection>, limit: Int): List<TermResult> {
        val formsByReading = LinkedHashMap<String, MutableList<Deinflection>>()
        val values = LinkedHashSet<String>()
        for (form in forms) {
            for (variant in listOf(form.text, Kana.toHiragana(form.text), Kana.toKatakana(form.text))) {
                values.add(variant)
                val matches = formsByReading.getOrPut(Kana.toHiragana(variant)) { ArrayList() }
                if (form !in matches) matches.add(form)
            }
        }
        if (values.isEmpty()) return emptyList()

        val seen = HashSet<Long>()
        val results = ArrayList<TermResult>()
        for (chunk in values.chunked(QUERY_CHUNK)) {
            val placeholders = chunk.joinToString(", ") { "?" }
            val rows = database.query(
                """
                SELECT t.id, t.dictionaryId, d.title, t.expression, t.reading, t.definitionTags, t.termTags,
                       t.rules, t.score, t.sequence, t.glossaryBlock, t.glossaryOffset, t.glossaryLength
                FROM terms t
                JOIN dictionaries d ON d.id = t.dictionaryId
                WHERE d.enabled = 1
                  AND (t.expression IN ($placeholders) OR t.reading IN ($placeholders))
                ORDER BY d.priority ASC, t.score DESC, LENGTH(t.expression) ASC
                """,
                chunk + chunk,
            )
            for (term in termResults(rows)) {
                val matches = ArrayList<Deinflection>()
                formsByReading[Kana.toHiragana(term.expression)]?.let { matches.addAll(it) }
                formsByReading[Kana.toHiragana(term.reading)]?.let { matches.addAll(it) }
                val form = matches.firstOrNull {
                    DeinflectionTags.matches(term.partsOfSpeech, it.tags)
                } ?: continue
                if (seen.add(term.termId)) results.add(term.copy(deinflection = form))
            }
        }
        results.sortWith(
            compareByDescending<TermResult> { it.deinflection?.text?.length ?: 0 }
                .thenByDescending { it.score }
                .thenBy { it.expression.length }
                .thenBy { it.expression },
        )
        return results.take(limit)
    }

    private fun queryFts(match: String, limit: Int): List<TermResult> = termResults(
        database.query(
            """
            SELECT t.id, t.dictionaryId, d.title, t.expression, t.reading, t.definitionTags, t.termTags,
                   t.rules, t.score, t.sequence, t.glossaryBlock, t.glossaryOffset, t.glossaryLength
            FROM terms_fts
            JOIN terms t ON t.id = terms_fts.rowid
            JOIN dictionaries d ON d.id = t.dictionaryId
            WHERE terms_fts MATCH ? AND d.enabled = 1
            ORDER BY d.priority ASC, t.score DESC, LENGTH(t.expression) ASC
            LIMIT ?
            """,
            listOf(match, limit),
        ),
    )

    fun kanji(character: String, limit: Int = 8): List<KanjiResult> {
        val value = character.trim()
        if (value.isEmpty()) return emptyList()
        return database.query(
            """
            SELECT k.id, k.dictionaryId, d.title, k.character, k.onyomi, k.kunyomi, k.tags, k.meanings, k.stats
            FROM kanji k
            JOIN dictionaries d ON d.id = k.dictionaryId
            WHERE d.enabled = 1 AND k.character = ?
            ORDER BY d.priority ASC
            LIMIT ?
            """,
            listOf(value, limit),
        ).map { kanjiResult(it) }
    }

    /**
     * The dictionary words whose expression contains [character], for the kanji detail
     * screen. The FTS column filter keeps the scan inside the index instead of matching
     * a wildcard against every expression in the database.
     */
    fun termsContaining(character: String, limit: Int = 40): List<TermResult> {
        val value = character.trim()
        if (value.isEmpty() || limit <= 0) return emptyList()
        val match = FtsText.buildQuery(value) ?: return emptyList()
        // FTS4 column filters do not accept a quoted phrase after the colon, so the
        // phrase is split into one filtered token per character.
        val filtered = match.split(' ').joinToString(" ") { token -> "expression:${token.trim('"')}" }
        return termResults(
            database.query(
                """
                SELECT t.id, t.dictionaryId, d.title, t.expression, t.reading, t.definitionTags, t.termTags,
                       t.rules, t.score, t.sequence, t.glossaryBlock, t.glossaryOffset, t.glossaryLength
                FROM terms_fts
                JOIN terms t ON t.id = terms_fts.rowid
                JOIN dictionaries d ON d.id = t.dictionaryId
                WHERE terms_fts MATCH ? AND d.enabled = 1
                ORDER BY d.priority ASC, t.score DESC, LENGTH(t.expression) ASC
                LIMIT ?
                """,
                listOf(filtered, limit),
            ),
        )
    }

    fun searchKanji(query: String, limit: Int = 20): List<KanjiResult> {
        val match = FtsText.buildQuery(query) ?: return emptyList()
        return database.query(
            """
            SELECT k.id, k.dictionaryId, d.title, k.character, k.onyomi, k.kunyomi, k.tags, k.meanings, k.stats
            FROM kanji_fts
            JOIN kanji k ON k.id = kanji_fts.rowid
            JOIN dictionaries d ON d.id = k.dictionaryId
            WHERE kanji_fts MATCH ? AND d.enabled = 1
            ORDER BY d.priority ASC, LENGTH(k.character) ASC
            LIMIT ?
            """,
            listOf(match, limit),
        ).map { kanjiResult(it) }
    }

    private fun termResults(rows: List<List<Any?>>): List<TermResult> {
        if (rows.isEmpty()) return emptyList()
        val blocks = loadBlocks(rows.mapTo(HashSet(rows.size)) { it[10] as Long })
        return rows.map { row ->
            val block = blocks[row[10] as Long]
            val glossary = if (block == null) {
                JsonArray(emptyList())
            } else {
                val offset = (row[11] as Number).toInt()
                val length = (row[12] as Number).toInt()
                Json.parseToJsonElement(String(block, offset, length, Charsets.UTF_8)) as? JsonArray
                    ?: JsonArray(emptyList())
            }
            TermResult(
                termId = row[0] as Long,
                dictionaryId = row[1] as Long,
                dictionaryTitle = row[2] as String,
                expression = row[3] as String,
                reading = row[4] as String,
                definitionTags = row[5] as String,
                termTags = row[6] as String,
                rules = row[7] as String,
                score = (row[8] as Number).toDouble(),
                sequence = (row[9] as Number?)?.toLong(),
                glossary = glossary,
            )
        }
    }

    /** Inflates each block once, even when many results share it. */
    private fun loadBlocks(ids: Set<Long>): Map<Long, ByteArray> {
        if (ids.isEmpty()) return emptyMap()
        val blocks = HashMap<Long, ByteArray>(ids.size)
        val list = ids.toList()
        var index = 0
        while (index < list.size) {
            val chunk = list.subList(index, minOf(index + 500, list.size))
            val placeholders = chunk.joinToString(", ") { "?" }
            database.query(
                "SELECT id, data FROM glossary_blocks WHERE id IN ($placeholders)",
                chunk,
            ).forEach { row ->
                blocks[row[0] as Long] = JsonCompression.inflate(row[1] as ByteArray)
            }
            index += 500
        }
        return blocks
    }

    private fun kanjiResult(row: List<Any?>): KanjiResult = KanjiResult(
        kanjiId = row[0] as Long,
        dictionaryId = row[1] as Long,
        dictionaryTitle = row[2] as String,
        character = row[3] as String,
        onyomi = row[4] as String,
        kunyomi = row[5] as String,
        tags = row[6] as String,
        meanings = Json.parseToJsonElement(row[7] as String) as? JsonArray ?: JsonArray(emptyList()),
        stats = Json.parseToJsonElement(row[8] as String) as? JsonObject ?: JsonObject(emptyMap()),
    )
}
