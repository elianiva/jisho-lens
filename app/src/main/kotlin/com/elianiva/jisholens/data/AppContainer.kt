package com.elianiva.jisholens.data

import android.content.Context
import com.elianiva.jisholens.dictionary.DictionaryRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database = DictionaryDatabase(appContext)
    val repository = DictionaryRepository(database)
    val settings = SettingsRepository(appContext)
    val notes = NotesRepository(appContext)
    val importController = ImportController()
    val ocr = OcrEngine()
    val components = ComponentManager(listOf(OcrModelComponent(appContext)), scope)

    val dictionaries = MutableStateFlow<List<DictionaryRecord>>(emptyList())

    init {
        // Every part the app needs but does not ship downloads now, in the background,
        // while the reader is still on the first screen.
        components.start()
    }

    suspend fun refreshDictionaries() {
        runCatching { repository.dictionaries() }
            .onSuccess { dictionaries.value = it }
    }

    fun fileSize(): Long = database.fileSize()
}
