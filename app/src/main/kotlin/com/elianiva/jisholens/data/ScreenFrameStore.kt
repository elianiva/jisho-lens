package com.elianiva.jisholens.data

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the last screen capture in memory for the frozen reader.
 *
 * A frame is never written to a file and never travels through an Intent: a
 * full-screen bitmap is far past the Binder transaction limit. [generation]
 * changes with every frame, so an open reader reloads itself when a rescan
 * arrives.
 *
 * [clear] drops the reference instead of recycling: Compose may still draw the
 * bitmap while the reader animates out, and a recycled bitmap crashes the
 * renderer. The garbage collector releases the pixels.
 */
class ScreenFrameStore {
    private var current: Bitmap? = null
    private val _generation = MutableStateFlow(0L)

    val generation: StateFlow<Long> = _generation.asStateFlow()

    @get:Synchronized
    val frame: Bitmap?
        get() = current

    @Synchronized
    fun put(bitmap: Bitmap) {
        current = bitmap
        _generation.value += 1
    }

    @Synchronized
    fun clear() {
        current = null
    }
}
