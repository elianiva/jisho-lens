package com.elianiva.jisholens.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elianiva.jisholens.data.TextSpeaker

/**
 * The screen's Japanese voice, or null when the device has none. The engine lives
 * with the composition and is shut down when the screen leaves it.
 */
@Composable
fun rememberTextSpeaker(): TextSpeaker? {
    val context = LocalContext.current
    val speaker = remember(context.applicationContext) { TextSpeaker(context.applicationContext) }
    val available by speaker.available.collectAsStateWithLifecycle()
    DisposableEffect(speaker) {
        onDispose { speaker.close() }
    }
    return speaker.takeIf { available }
}
