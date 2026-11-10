package com.elianiva.jisholens.domain

/** How much of a page the reader already knows, counted in unique dictionary headwords. */
data class Coverage(val known: Int, val total: Int) {
    val percent: Int get() = if (total == 0) 0 else known * 100 / total
}

/**
 * Counts the unique headwords among [words] and how many of them are in [known].
 * A word with no dictionary entry is not counted: it is grammar, not vocabulary,
 * and marking it as known would mean nothing.
 */
fun pageCoverage(words: List<ScanWord>, known: Set<String>): Coverage {
    val headwords = words.flatMapTo(HashSet()) { word -> word.entries.map { it.expression } }
    if (headwords.isEmpty()) return Coverage(0, 0)
    return Coverage(known = headwords.count { it in known }, total = headwords.size)
}

/** True when [word] is one of the headwords the reader marked as known. */
fun ScanWord.isKnown(known: Set<String>): Boolean = entries.any { it.expression in known }

/** The headword to record for [word]: the first entry that the dictionary ranked. */
fun ScanWord.headword(): String? = entries.firstOrNull()?.expression
