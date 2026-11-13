package com.elianiva.jisholens.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.notesDataStore by preferencesDataStore(name = "notes")

/**
 * Short notes and mnemonics, one per headword, kept on the device.
 *
 * A note is keyed by the expression, not by a dictionary row, so the same note
 * follows 食べる across every dictionary that has the word. A blank note removes
 * the key instead of storing an empty string.
 */
class NotesRepository(context: Context) {
    private val dataStore = context.applicationContext.notesDataStore

    val notes: Flow<Map<String, String>> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { preferences ->
            preferences.asMap()
                .mapNotNull { (key, value) ->
                    val expression = key.name.removePrefix(PREFIX)
                    val note = value as? String
                    if (key.name.startsWith(PREFIX) && !note.isNullOrBlank()) expression to note else null
                }
                .toMap()
        }

    suspend fun setNote(expression: String, note: String) {
        val word = expression.trim()
        if (word.isEmpty()) return
        val key = stringPreferencesKey(PREFIX + word)
        val clean = note.trim()
        dataStore.edit { preferences ->
            if (clean.isEmpty()) preferences.remove(key) else preferences[key] = clean
        }
    }

    private companion object {
        const val PREFIX = "note:"
    }
}
