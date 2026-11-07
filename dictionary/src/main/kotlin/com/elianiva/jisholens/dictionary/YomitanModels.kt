package com.elianiva.jisholens.dictionary

/**
 * The `index.json` of a Yomitan dictionary archive.
 *
 * Everything the app shows about an installed dictionary comes from here,
 * including the stylesheet Yitendex-style dictionaries ship.
 */
data class YomitanIndex(
    val title: String,
    val formatVersion: Int,
    val revision: String,
    val sequenced: Boolean,
    val prefixWildcardsSupported: Boolean,
    val frequencyMode: String?,
    val sourceLanguage: String?,
    val targetLanguage: String?,
    val author: String?,
    val url: String?,
    val description: String?,
    val attribution: String?,
    val isUpdatable: Boolean?,
    val indexUrl: String?,
    val downloadUrl: String?,
    val styles: String?,
    val tagMeta: List<YomitanTag>,
)

/** A tag from a `tag_bank` or from the legacy `tagMeta` object of `index.json`. */
data class YomitanTag(
    val name: String,
    val category: String = "default",
    val order: Int = 0,
    val notes: String = "",
    val score: Double = 0.0,
)
