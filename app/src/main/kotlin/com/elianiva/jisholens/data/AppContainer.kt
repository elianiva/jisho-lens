package com.elianiva.jisholens.data

import android.content.Context
import com.elianiva.jisholens.dictionary.DictionaryRecord
import kotlinx.coroutines.flow.MutableStateFlow

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database = DictionaryDatabase(appContext)
    val repository = DictionaryRepository(database)
    val settings = SettingsRepository(appContext)
    val importController = ImportController()
    val ocr = OcrEngine()
    val screenFrames = ScreenFrameStore()

    val dictionaries = MutableStateFlow<List<DictionaryRecord>>(emptyList())

    suspend fun refreshDictionaries() {
        runCatching { repository.dictionaries() }
            .onSuccess { dictionaries.value = it }
    }

    fun fileSize(): Long = database.fileSize()
}
