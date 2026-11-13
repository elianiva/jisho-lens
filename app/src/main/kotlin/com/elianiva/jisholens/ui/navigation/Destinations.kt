package com.elianiva.jisholens.ui.navigation

import kotlinx.serialization.Serializable

/** The lookup tab. [query] opens it with a word, such as one tapped on a kanji screen. */
@Serializable
data class SearchRoute(val query: String? = null)

@Serializable
data object ScanHomeRoute

@Serializable
data object ReaderRoute

@Serializable
data object SettingsRoute

@Serializable
data object DictionariesRoute

@Serializable
data object OnboardingRoute

@Serializable
data class ScanRoute(val imagePath: String)

@Serializable
data class KanjiRoute(val character: String)
