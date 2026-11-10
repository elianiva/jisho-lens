package com.elianiva.jisholens.data

import com.elianiva.jisholens.dictionary.DictionaryLookup
import com.elianiva.jisholens.dictionary.DictionaryRecord
import com.elianiva.jisholens.dictionary.DictionaryStore
import com.elianiva.jisholens.dictionary.Furigana
import com.elianiva.jisholens.dictionary.ImportSummary
import com.elianiva.jisholens.dictionary.ScanMatch
import com.elianiva.jisholens.dictionary.TermResult
import com.elianiva.jisholens.dictionary.YomitanArchive
import com.elianiva.jisholens.dictionary.YomitanImporter
import com.elianiva.jisholens.domain.Entry
import com.elianiva.jisholens.domain.EntryTag
import com.elianiva.jisholens.domain.PitchAccent
import com.elianiva.jisholens.domain.ScanWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.CoroutineContext

class DictionaryRepository(private val database: DictionaryDatabase) {
    private val dispatcher: CoroutineContext = Dispatchers.IO.limitedParallelism(1)

    private val store: DictionaryStore by lazy { DictionaryStore(database.sqlite) }
    private val lookup: DictionaryLookup by lazy { DictionaryLookup(database.sqlite) }

    suspend fun dictionaries(): List<DictionaryRecord> = onDb { store.list() }

    suspend fun lookup(text: String): List<Entry> = onDb { entries(lookup.lookup(text.trim())) }

    suspend fun search(text: String): List<Entry> = onDb { entries(lookup.search(text.trim())) }

    suspend fun scan(text: String): List<ScanWord> = onDb {
        val matches = lookup.scan(text)
        val entriesById = entries(matches.flatMap { it.results })
            .associateBy { it.termId }
        // The segmentation stays greedy; the entries of each word are ranked by
        // frequency so the chip and its card lead with the common word.
        matches.map { match -> match.toScanWord(entriesById) }
            .map { word -> word.copy(entries = rankEntries(word.entries)) }
    }

    /**
     * The words that start at [offset] in [text], best first. A tap on a frozen screen
     * uses this instead of [scan] so the card can offer 日本 alongside the longer
     * 日本語 that [scan] would greedily keep.
     */
    suspend fun scanAt(text: String, offset: Int, limit: Int = 8): List<ScanWord> = onDb {
        val matches = lookup.scanAt(text, offset, limit = limit)
        val entriesById = entries(matches.flatMap { it.results })
            .associateBy { it.termId }
        rankCandidates(matches.map { match -> match.toScanWord(entriesById) })
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) = onDb { store.setEnabled(id, enabled) }

    suspend fun move(id: Long, delta: Int): Boolean = onDb { store.move(id, delta) }

    suspend fun delete(id: Long) = onDb {
        store.delete(id)
        database.sqlite.execute("VACUUM")
    }

    suspend fun import(
        archiveFile: File,
        alias: String? = null,
        onProgress: (String) -> Unit = {},
        shouldContinue: () -> Boolean = { true },
    ): ImportSummary = onDb {
        YomitanArchive.open(archiveFile).use { archive ->
            YomitanImporter(
                database = database.sqlite,
                onProgress = onProgress,
                shouldContinue = shouldContinue,
            ).import(archive, alias)
        }
    }

    suspend fun media(dictionaryId: Long, path: String): ByteArray? = onDb {
        store.media(dictionaryId, path)?.data
    }

    suspend fun styles(dictionaryId: Long): String? = onDb { store.styles(dictionaryId) }

    fun fileSize(): Long = database.fileSize()

    private suspend fun <T> onDb(block: () -> T): T = withContext(dispatcher) { block() }

    private fun entries(terms: List<TermResult>): List<Entry> {
        if (terms.isEmpty()) return emptyList()
        val tags = store.tags().associateBy { it.name }
        val meta = termMeta(terms)
        return terms.map { term ->
            Entry(
                termId = term.termId,
                dictionaryId = term.dictionaryId,
                dictionaryTitle = term.dictionaryTitle,
                expression = term.expression,
                reading = term.reading,
                ruby = Furigana.align(term.expression, term.reading),
                tags = splitTags(term).map { name ->
                    EntryTag(name = name, notes = tags[name]?.notes?.takeIf { it.isNotBlank() })
                },
                glossary = term.glossary,
                frequency = meta.frequencies[term.expression],
                score = term.score,
                deinflection = term.deinflection?.originalText,
                deinflectionReasons = term.deinflection?.reasons.orEmpty(),
                pitch = meta.pitches[term.expression].orEmpty(),
            )
        }
    }

    private fun splitTags(term: TermResult): List<String> =
        (term.definitionTags.split(' ') + term.termTags.split(' '))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    private class TermMeta(
        val frequencies: Map<String, Double>,
        val pitches: Map<String, List<PitchAccent>>,
    )

    /** One pass over `term_meta` for every expression in [terms]. */
    private fun termMeta(terms: List<TermResult>): TermMeta {
        val expressions = terms.flatMap { listOf(it.expression, it.reading) }.toSet()
        val frequencies = HashMap<String, Double>()
        val pitches = HashMap<String, MutableList<PitchAccent>>()
        for (record in store.termMeta(expressions)) {
            when (record.mode) {
                "freq" -> TermMetaParser.frequency(record.data)?.let {
                    frequencies.putIfAbsent(record.expression, it)
                }
                "pitch" -> {
                    val accents = pitches.getOrPut(record.expression) { ArrayList(1) }
                    TermMetaParser.pitch(record.data).forEach { accent ->
                        if (accents.none { it.position == accent.position && it.reading == accent.reading }) {
                            accents.add(accent)
                        }
                    }
                }
            }
        }
        return TermMeta(frequencies, pitches)
    }

    private fun ScanMatch.toScanWord(entriesById: Map<Long, Entry>): ScanWord = ScanWord(
        surface = surface,
        start = start,
        end = end,
        entries = results.mapNotNull { entriesById[it.termId] },
        exact = exact,
    )
}
