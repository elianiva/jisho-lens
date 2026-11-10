package com.elianiva.jisholens.data

import com.elianiva.jisholens.domain.Entry
import com.elianiva.jisholens.domain.ScanWord

/**
 * Frequency ranks come from the dictionary's `freq` metadata, where a lower number
 * means a more common word. Entries without a rank sort last; the term score and the
 * dictionary order break the remaining ties.
 */
internal fun rankEntries(entries: List<Entry>): List<Entry> = entries.sortedWith(
    compareBy<Entry> { it.frequency == null }
        .thenBy { it.frequency ?: Double.MAX_VALUE }
        .thenByDescending { it.score },
)

/**
 * Orders the words that start at one position so the reader sees the likely answer
 * first: an exact headword before a conjugated form, then the more common word, then
 * the longer surface. Without frequency data this keeps the lexical order that
 * [com.elianiva.jisholens.dictionary.DictionaryLookup.scanAt] returns.
 */
internal fun rankCandidates(words: List<ScanWord>): List<ScanWord> = words
    .map { word -> word.copy(entries = rankEntries(word.entries)) }
    .sortedWith(
        compareByDescending<ScanWord> { it.exact }
            .thenBy { it.bestFrequency ?: Double.MAX_VALUE }
            .thenByDescending { it.length },
    )
