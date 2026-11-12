package com.elianiva.jisholens.domain

import com.elianiva.jisholens.dictionary.RubySegment
import kotlinx.serialization.json.JsonArray

data class EntryTag(val name: String, val notes: String?)

data class Entry(
    val termId: Long,
    val dictionaryId: Long,
    val dictionaryTitle: String,
    val expression: String,
    val reading: String,
    val ruby: List<RubySegment>,
    val tags: List<EntryTag>,
    val glossary: JsonArray,
    val frequency: Double?,
    val score: Double,
    /** The inflected surface that was looked up, when the entry was reached by deinflection. */
    val deinflection: String?,
    /** The rules that were applied to reach the entry, such as causative and passive. */
    val deinflectionReasons: List<String> = emptyList(),
    /** Pitch accents from the dictionary's `pitch` metadata, usually one. */
    val pitch: List<PitchAccent> = emptyList(),
)

data class ScanWord(
    val surface: String,
    val start: Int,
    val end: Int,
    val entries: List<Entry>,
    /** True when the surface itself is a headword, not a conjugated form. */
    val exact: Boolean = true,
    /** Order of the tap candidate list; lower is a better answer for the tap. */
    val priority: Int = 0,
) {
    /** The best frequency rank among the entries; lower is more common. */
    val bestFrequency: Double? get() = entries.mapNotNull { it.frequency }.minOrNull()

    val length: Int get() = end - start
}
