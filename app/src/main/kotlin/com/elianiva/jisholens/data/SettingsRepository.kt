package com.elianiva.jisholens.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

enum class AppTheme {
    SYSTEM,
    LIGHT,
    DARK;

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    val label: String
        get() = when (this) {
            SYSTEM -> "System"
            LIGHT -> "Light"
            DARK -> "Dark"
        }
}

data class Settings(
    val theme: AppTheme = AppTheme.SYSTEM,
    val fullTextSearch: Boolean = false,
    val dynamicColor: Boolean = true,
    val onboardingComplete: Boolean = false,
)

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.settingsDataStore

    val settings: Flow<Settings> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { preferences ->
            Settings(
                theme = runCatching {
                    AppTheme.valueOf(preferences[THEME].orEmpty())
                }.getOrDefault(AppTheme.SYSTEM),
                fullTextSearch = preferences[FULL_TEXT] ?: false,
                dynamicColor = preferences[DYNAMIC_COLOR] ?: true,
                onboardingComplete = preferences[ONBOARDING] ?: false,
            )
        }

    suspend fun setTheme(theme: AppTheme) {
        dataStore.edit { it[THEME] = theme.name }
    }

    /** Latest lookups first, without duplicates. */
    val recentSearches: Flow<List<String>> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { preferences -> preferences[RECENTS].orEmpty().split('\n').filter { it.isNotBlank() } }

    suspend fun addRecentSearch(query: String) {
        val clean = query.trim().replace('\n', ' ')
        if (clean.isEmpty()) return
        dataStore.edit { preferences ->
            val current = preferences[RECENTS].orEmpty().split('\n').filter { it.isNotBlank() }
            preferences[RECENTS] = (listOf(clean) + current.filter { it != clean })
                .take(MAX_RECENTS)
                .joinToString("\n")
        }
    }

    suspend fun clearRecentSearches() {
        dataStore.edit { it.remove(RECENTS) }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[DYNAMIC_COLOR] = enabled }
    }

    suspend fun setFullTextSearch(enabled: Boolean) {
        dataStore.edit { it[FULL_TEXT] = enabled }
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        dataStore.edit { it[ONBOARDING] = complete }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val FULL_TEXT = booleanPreferencesKey("full_text_search")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val RECENTS = stringPreferencesKey("recent_searches")
        const val MAX_RECENTS = 12
        val ONBOARDING = booleanPreferencesKey("onboarding_complete")
    }
}
