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
 * Orders the words that start at one position. The dictionary layer already ranked
 * them from the tokenizer evidence (the word under the tap first, then reading
 * matches), so its `priority` leads; frequency only breaks a tie between words the
 * segmenter could not separate. Without frequency data the lexical order stands.
 */
internal fun rankCandidates(words: List<ScanWord>): List<ScanWord> = words
    .map { word -> word.copy(entries = rankEntries(word.entries)) }
    .sortedWith(
        compareBy<ScanWord> { it.priority }
            .thenBy { it.bestFrequency ?: Double.MAX_VALUE }
            .thenByDescending { it.length },
    )
