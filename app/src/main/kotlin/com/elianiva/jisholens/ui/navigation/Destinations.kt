package com.elianiva.jisholens.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object SearchRoute

@Serializable
data object ScanHomeRoute

@Serializable
data object SettingsRoute

@Serializable
data object DictionariesRoute

@Serializable
data object OnboardingRoute

@Serializable
data class ScanRoute(val imagePath: String)
