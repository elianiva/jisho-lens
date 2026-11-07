package com.elianiva.jisholens.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

object TermMetaParser {
    /** Reads Yomitan's number, string, or `{value, displayValue}` frequency data. */
    fun frequency(data: String): Double? = runCatching {
        when (val element = Json.parseToJsonElement(data)) {
            is JsonPrimitive -> element.doubleOrNull ?: element.contentOrNull?.toDoubleOrNull()
            is JsonObject -> (element["value"] as? JsonPrimitive)?.doubleOrNull
            else -> null
        }
    }.getOrNull()
}
