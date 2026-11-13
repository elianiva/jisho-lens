package com.elianiva.jisholens.domain

import com.elianiva.jisholens.dictionary.KanjiResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** The five KANJIDIC statistics the detail screen shows. Any of them may be absent. */
data class KanjiStats(
    val grade: Int? = null,
    val jlpt: Int? = null,
    val frequency: Int? = null,
    val strokes: Int? = null,
    val radical: String? = null,
) {
    val isEmpty: Boolean
        get() = grade == null && jlpt == null && frequency == null && strokes == null && radical == null
}

/**
 * Reads the `stats` object of a Yomitan kanji bank row.
 *
 * Yomitan's schema allows any string key, and the KANJIDIC export has used both
 * `freq` and `frequency` and has carried numbers and numeric strings, so the parser
 * accepts the known spellings and both JSON forms.
 */
fun parseKanjiStats(stats: JsonObject): KanjiStats {
    fun number(vararg keys: String): Int? = keys.firstNotNullOfOrNull { key ->
        (stats[key] as? JsonPrimitive)?.intOrNull
    }

    fun text(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        (stats[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    }

    return KanjiStats(
        grade = number("grade"),
        jlpt = number("jlpt", "jlptLevel"),
        frequency = number("freq", "frequency"),
        strokes = number("strokes", "strokeCount", "stroke_count"),
        radical = text("radical", "radicalName"),
    )
}

/** A kanji row with the fields the detail screen reads, parsed once from the dictionary. */
data class KanjiDetail(
    val character: String,
    val onyomi: List<String>,
    val kunyomi: List<String>,
    val meanings: JsonArray,
    val tags: List<EntryTag>,
    val dictionaryTitle: String,
    val stats: KanjiStats,
)

fun KanjiResult.toDetail(tagNotes: Map<String, String> = emptyMap()): KanjiDetail = KanjiDetail(
    character = character,
    onyomi = readings(onyomi),
    kunyomi = readings(kunyomi),
    meanings = meanings,
    tags = tags.split(' ', ',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .map { name -> EntryTag(name = name, notes = tagNotes[name]?.takeIf { it.isNotBlank() }) },
    dictionaryTitle = dictionaryTitle,
    stats = parseKanjiStats(stats),
)

/** Yomitan keeps readings space-separated; some banks use commas. */
private fun readings(value: String): List<String> =
    value.split(' ', ',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
