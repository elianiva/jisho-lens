package com.elianiva.jisholens.data

object SearchQuery {
    /**
     * English glosses always use FTS, matching the Flutter app. Japanese uses
     * exact lookup unless the full-text setting is on.
     */
    fun useFullText(query: String, fullTextSetting: Boolean): Boolean =
        fullTextSetting || query.any { it in 'a'..'z' || it in 'A'..'Z' }
}
