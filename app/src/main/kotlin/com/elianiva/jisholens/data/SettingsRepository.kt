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
            SYSTEM -> "Follows system"
            LIGHT -> "Light"
            DARK -> "Dark"
        }
}

data class Settings(
    val theme: AppTheme = AppTheme.SYSTEM,
    val fullTextSearch: Boolean = false,
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
                onboardingComplete = preferences[ONBOARDING] ?: false,
            )
        }

    suspend fun setTheme(theme: AppTheme) {
        dataStore.edit { it[THEME] = theme.name }
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
        val ONBOARDING = booleanPreferencesKey("onboarding_complete")
    }
}
