package com.elianiva.jisholens.data

import com.elianiva.jisholens.domain.PitchAccent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

object TermMetaParser {
    /** Reads Yomitan's number, string, or `{value, displayValue}` frequency data. */
    fun frequency(data: String): Double? = runCatching {
        when (val element = Json.parseToJsonElement(data)) {
            is JsonPrimitive -> element.doubleOrNull ?: element.contentOrNull?.toDoubleOrNull()
            is JsonObject -> (element["value"] as? JsonPrimitive)?.doubleOrNull
            else -> null
        }
    }.getOrNull()

    /**
     * Reads Yomitan's `pitch` data:
     * `{"reading":"たべる","pitches":[{"position":1,"tags":[]}]}`.
     *
     * Some dictionaries store only the array, and older ones a bare number, so the
     * parser accepts an object with a reading, an array, or a number. The reading is
     * optional; callers fall back to the term's own reading.
     */
    fun pitch(data: String): List<PitchAccent> = runCatching {
        when (val element = Json.parseToJsonElement(data)) {
            is JsonObject -> {
                val reading = (element["reading"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                parseEntries(element["pitches"], reading)
            }
            is JsonArray -> parseEntries(element, "")
            is JsonPrimitive -> element.intOrNull?.let { listOf(PitchAccent("", it)) }.orEmpty()
            else -> emptyList()
        }
    }.getOrDefault(emptyList())

    private fun parseEntries(element: JsonElement?, reading: String): List<PitchAccent> {
        val array = element as? JsonArray ?: return emptyList()
        return array.mapNotNull { entry ->
            val position = when (entry) {
                is JsonPrimitive -> entry.intOrNull
                is JsonObject -> (entry["position"] as? JsonPrimitive)?.intOrNull
                else -> null
            } ?: return@mapNotNull null
            val tags = (entry as? JsonObject)?.get("tags")?.let { tagsElement ->
                (tagsElement as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
            }.orEmpty()
            PitchAccent(reading = reading, position = position, tags = tags)
        }
    }
}
