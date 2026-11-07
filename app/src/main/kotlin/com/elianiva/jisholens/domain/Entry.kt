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
    val deinflection: String?,
)

data class ScanWord(
    val surface: String,
    val start: Int,
    val end: Int,
    val entries: List<Entry>,
)
