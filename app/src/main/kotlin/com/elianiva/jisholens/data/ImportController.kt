package com.elianiva.jisholens.data

import com.elianiva.jisholens.dictionary.ImportSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface ImportState {
    data object Idle : ImportState
    data class Copying(val name: String, val bytes: Long, val total: Long?) : ImportState
    data class Importing(val title: String, val message: String) : ImportState
    data class Done(val summary: ImportSummary) : ImportState
    data class Failed(val message: String) : ImportState
    data object Cancelled : ImportState
}

/** Process-wide import state, observed by the dictionary screen. */
class ImportController {
    private val mutableState = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = mutableState.asStateFlow()

    @Volatile
    var cancelRequested: Boolean = false
        private set

    fun reset() {
        cancelRequested = false
        mutableState.value = ImportState.Idle
    }

    fun update(state: ImportState) {
        mutableState.value = state
    }

    fun requestCancel() {
        cancelRequested = true
    }
}
