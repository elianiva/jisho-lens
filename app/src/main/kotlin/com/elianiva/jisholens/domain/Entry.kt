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
)

data class ScanWord(
    val surface: String,
    val start: Int,
    val end: Int,
    val entries: List<Entry>,
)
