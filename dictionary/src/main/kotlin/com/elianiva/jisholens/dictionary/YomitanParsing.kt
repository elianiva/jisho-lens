package com.elianiva.jisholens.dictionary

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

internal object YomitanParsing {
    fun index(element: JsonElement, stylesCss: String?): YomitanIndex {
        val objectValue = element as? JsonObject
            ?: throw YomitanFormatException("index.json is not a JSON object")
        val title = objectValue.string("title")?.takeIf { it.isNotBlank() }
            ?: throw YomitanFormatException("index.json has no title")
        return YomitanIndex(
            title = title,
            formatVersion = objectValue.int("format") ?: 1,
            revision = objectValue["revision"]?.let { primitiveText(it) }.orEmpty(),
            sequenced = objectValue.boolean("sequenced") ?: false,
            prefixWildcardsSupported = objectValue.boolean("prefixWildcardsSupported") ?: false,
            frequencyMode = objectValue.string("frequencyMode"),
            sourceLanguage = objectValue.language("sourceLanguage"),
            targetLanguage = objectValue.language("targetLanguage"),
            author = objectValue.string("author"),
            url = objectValue.string("url"),
            description = objectValue.string("description"),
            attribution = objectValue.string("attribution"),
            isUpdatable = objectValue.boolean("isUpdatable"),
            indexUrl = objectValue.string("indexUrl"),
            downloadUrl = objectValue.string("downloadUrl"),
            styles = objectValue.string("styles") ?: stylesCss,
            tagMeta = parseTagMeta(objectValue["tagMeta"]),
        )
    }

    fun tag(row: JsonRow): YomitanTag? {
        val name = row.string(0) ?: return null
        if (name.isEmpty()) return null
        return YomitanTag(
            name = name,
            category = row.string(1)?.ifEmpty { null } ?: "default",
            order = row.long(2)?.toInt() ?: 0,
            notes = row.string(3).orEmpty(),
            score = row.number(4),
        )
    }

    /** Tag metadata from index.json: `{name: {category, order, notes, score}}` or `{name: [category, order, notes, score]}`. */
    private fun parseTagMeta(element: JsonElement?): List<YomitanTag> {
        val objectValue = element as? JsonObject ?: return emptyList()
        return objectValue.mapNotNull { (name, value) ->
            when (value) {
                is JsonObject -> YomitanTag(
                    name = name,
                    category = value.string("category")?.ifEmpty { null } ?: "default",
                    order = value.int("order") ?: 0,
                    notes = value.string("notes").orEmpty(),
                    score = value.number("score") ?: 0.0,
                )
                is JsonArray -> YomitanTag(
                    name = name,
                    category = value.string(0)?.ifEmpty { null } ?: "default",
                    order = value.int(1) ?: 0,
                    notes = value.string(2).orEmpty(),
                    score = value.number(3) ?: 0.0,
                )
                else -> null
            }
        }
    }

    fun primitiveText(element: JsonElement): String? =
        (element as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content
}

private fun JsonArray.string(index: Int): String? =
    (getOrNull(index) as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull

private fun JsonArray.number(index: Int): Double? =
    (getOrNull(index) as? JsonPrimitive)?.takeUnless { it is JsonNull }?.doubleOrNull

private fun JsonArray.int(index: Int): Int? =
    (getOrNull(index) as? JsonPrimitive)?.takeUnless { it is JsonNull }?.intOrNull

private fun JsonObject.string(name: String): String? =
    (get(name) as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull

private fun JsonObject.number(name: String): Double? = (get(name) as? JsonPrimitive)?.doubleOrNull

private fun JsonObject.int(name: String): Int? = (get(name) as? JsonPrimitive)?.intOrNull

private fun JsonObject.boolean(name: String): Boolean? = (get(name) as? JsonPrimitive)?.booleanOrNull

/** Language fields are plain strings in current Yomitan, but older archives use `{code, name}`. */
private fun JsonObject.language(name: String): String? {
    val value = get(name) ?: return null
    return when (value) {
        is JsonPrimitive -> value.contentOrNull
        is JsonObject -> (value["code"] as? JsonPrimitive)?.contentOrNull
        else -> null
    }
}
