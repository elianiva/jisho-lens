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
}

/** A word found by [DictionaryLookup.scan] or [DictionaryLookup.scanAt], with its range in the text. */
data class ScanMatch(
    val surface: String,
    val start: Int,
    val end: Int,
    val results: List<TermResult>,
    /** True when the surface itself is a headword, not a conjugated form. */
    val exact: Boolean = false,
)

/** One prefix that starts at the tapped offset, and whether it is a dictionary headword itself. */
private class RankedCandidate(val match: ScanMatch, val exact: Boolean)

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

    fun lookup(text: String, limit: Int = 32): List<TermResult> {
        val value = text.trim()
        if (value.isEmpty() || limit <= 0) return emptyList()
        val exact = queryExact(value, limit)
        if (exact.size >= limit) return exact
        val deinflections = deinflector.deinflect(value)
            .filter { DeinflectionTags.isDictionaryForm(it.tags) }
        if (deinflections.isEmpty()) return exact
        val candidates = queryDeinflected(deinflections, limit)
        if (exact.isEmpty()) return candidates
        val seen = exact.mapTo(HashSet(exact.size)) { it.termId }
        return (exact + candidates.filter { seen.add(it.termId) }).take(limit)
    }

    /**
     * Finds the words in [text] without a morphological analyzer.
     *
     * Every Japanese prefix up to [maxLength] code points is collected, then
     * all prefixes are queried in one batch. Matches are selected greedily from
     * left to right, longest first, so a line becomes a small list of words
     * that a scan UI can show as chips.
     */
    fun scan(text: String, maxLength: Int = 12, limitPerWord: Int = 6): List<ScanMatch> {
        if (text.isEmpty() || maxLength <= 0) return emptyList()

        val rangesByCandidate = LinkedHashMap<String, MutableList<IntRange>>()
        var start = 0
        while (start < text.length) {
            val codePoint = text.codePointAt(start)
            if (!FtsText.isJapanese(codePoint)) {
                start += Character.charCount(codePoint)
                continue
            }
            var end = start
            var length = 0
            while (end < text.length && length < maxLength) {
                val next = text.codePointAt(end)
                if (!FtsText.isJapanese(next)) break
                end += Character.charCount(next)
                length++
                rangesByCandidate.getOrPut(text.substring(start, end)) { ArrayList() }
                    .add(start until end)
            }
            start += Character.charCount(codePoint)
        }
        if (rangesByCandidate.isEmpty()) return emptyList()

        val candidates = rangesByCandidate.keys
        val exact = exactCandidateResults(candidates, limitPerWord)
        val deinflected = deinflectedCandidateResults(candidates, limitPerWord)

        val byStart = HashMap<Int, MutableList<ScanMatch>>()
        for ((candidate, ranges) in rangesByCandidate) {
            val results = mergeResults(exact[candidate], deinflected[candidate], limitPerWord)
            if (results.isEmpty()) continue
            val exactMatch = !exact[candidate].isNullOrEmpty()
            for (range in ranges) {
                byStart.getOrPut(range.first) { ArrayList() }
                    .add(ScanMatch(candidate, range.first, range.last + 1, results, exactMatch))
            }
        }

        val matches = ArrayList<ScanMatch>()
        var index = 0
        while (index < text.length) {
            val best = byStart[index]?.maxByOrNull { it.end - it.start }
            if (best == null) {
                index += Character.charCount(text.codePointAt(index))
            } else {
                matches.add(best)
                index = best.end
            }
        }
        return matches
    }

    /**
     * Collects the words that start at [offset], so a tap can offer alternatives.
     *
     * [scan] keeps only the longest match at each position. A tap needs the shorter
     * prefixes too: in 日本語 the reader may mean 日本語, 日本, or 日. Every Japanese
     * prefix up to [maxLength] code points is queried in one batch, exactly as [scan]
     * does, and the candidates come back ordered by:
     *
     *  1. a prefix that is itself a headword, before one that only matches after
     *     deinflection;
     *  2. the longer surface first;
     *  3. the higher term score, then dictionary priority.
     *
     * The entries inside a candidate keep exact matches before deinflected ones. The
     * order is lexical on purpose: frequency ranking belongs to the caller, which
     * already resolves `term_meta`.
     */
    fun scanAt(text: String, offset: Int, maxLength: Int = 12, limit: Int = 8): List<ScanMatch> {
        if (offset !in text.indices || maxLength <= 0 || limit <= 0) return emptyList()
        if (!FtsText.isJapanese(text.codePointAt(offset))) return emptyList()

        val ranges = ArrayList<IntRange>(maxLength)
        var end = offset
        var length = 0
        while (end < text.length && length < maxLength) {
            val next = text.codePointAt(end)
            if (!FtsText.isJapanese(next)) break
            end += Character.charCount(next)
            length++
            ranges.add(offset until end)
        }
        val candidates = ranges.map { text.substring(it.first, it.last + 1) }
        val exact = exactCandidateResults(candidates, limit)
        val deinflected = deinflectedCandidateResults(candidates, limit)

        val ranked = ArrayList<RankedCandidate>(ranges.size)
        for ((index, range) in ranges.withIndex()) {
            val surface = candidates[index]
            val exactResults = exact[surface]
            val results = mergeResults(exactResults, deinflected[surface], limit)
            if (results.isEmpty()) continue
            ranked.add(
                RankedCandidate(
                    match = ScanMatch(surface, range.first, range.last + 1, results, !exactResults.isNullOrEmpty()),
                    exact = !exactResults.isNullOrEmpty(),
                ),
            )
        }
        return compressStems(ranked)
            .sortedWith(
                compareByDescending<RankedCandidate> { it.exact }
                    .thenByDescending { it.match.end - it.match.start }
                    .thenByDescending { it.match.results.firstOrNull()?.score ?: 0.0 },
            )
            .take(limit)
            .map { it.match }
    }

    /**
     * Keeps the alternatives that add something. Shorter deinflected prefixes are
     * stems of the longest one: 食べさせられ and 食べさせ both reach 食べる through
     * the same rule chain, so only 食べさせられた is worth offering. A headword
     * candidate is never dropped, and a deinflected prefix that adds an entry is kept.
     */
    private fun compressStems(candidates: List<RankedCandidate>): List<RankedCandidate> {
        val exact = candidates.filter { it.exact }
        val deinflected = candidates.filterNot { it.exact }
            .sortedByDescending { it.match.end - it.match.start }
        val longest = deinflected.firstOrNull() ?: return exact
        val offered = exact.flatMapTo(HashSet()) { candidate -> candidate.match.results.map { it.termId } }
        offered.addAll(longest.match.results.map { it.termId })
        val kept = ArrayList<RankedCandidate>(candidates.size)
        kept.addAll(exact)
        kept.add(longest)
        for (candidate in deinflected.drop(1)) {
            val ids = candidate.match.results.mapTo(HashSet()) { it.termId }
            if (offered.containsAll(ids)) continue
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
                    DeinflectionTags.matches(term.rules, term.definitionTags, it.tags)
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

    private fun exactCandidateResults(
        candidates: Collection<String>,
        limitPerWord: Int,
    ): Map<String, List<TermResult>> {
        val byHiragana = HashMap<String, MutableList<String>>(candidates.size)
        for (candidate in candidates) {
            byHiragana.getOrPut(Kana.toHiragana(candidate)) { ArrayList() }.add(candidate)
        }
        val result = HashMap<String, MutableList<TermResult>>()
        for (term in queryTermsForValues(candidates, limitPerWord)) {
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
        val values = LinkedHashSet<String>()
        for (candidate in candidates) {
            val forms = deinflector.deinflect(candidate)
                .filter { DeinflectionTags.isDictionaryForm(it.tags) }
            if (forms.isEmpty()) continue
            formsByCandidate[candidate] = forms
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
            val entries = result.getOrPut(candidate) { ArrayList() }
            for (term in terms) {
                val form = forms.firstOrNull { candidateForm ->
                    (Kana.toHiragana(candidateForm.text) == Kana.toHiragana(term.expression) ||
                        Kana.toHiragana(candidateForm.text) == Kana.toHiragana(term.reading)) &&
                        DeinflectionTags.matches(term.rules, term.definitionTags, candidateForm.tags)
                } ?: continue
                if (entries.none { it.termId == term.termId }) {
                    entries.add(term.copy(deinflection = form))
                }
                if (entries.size >= limitPerWord) break
            }
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
                    DeinflectionTags.matches(term.rules, term.definitionTags, it.tags)
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
