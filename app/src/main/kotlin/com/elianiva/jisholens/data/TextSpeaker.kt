package com.elianiva.jisholens.data

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Speaks Japanese with the device's text-to-speech engine.
 *
 * The engine is created once and shut down by the screen or service that owns
 * it. [available] stays false when the device has no Japanese voice, so callers
 * can hide the control instead of offering one that cannot speak.
 */
class TextSpeaker(context: Context) : TextToSpeech.OnInitListener {
    private val engine = TextToSpeech(context.applicationContext, this)
    private val _available = MutableStateFlow(false)
    val available: StateFlow<Boolean> = _available.asStateFlow()
    private var ready = false
    private var closed = false

    override fun onInit(status: Int) {
        if (closed || status != TextToSpeech.SUCCESS) return
        ready = runCatching {
            engine.language = Locale.JAPANESE
            val result = engine.setLanguage(Locale.JAPANESE)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) return@runCatching false
            engine.voices.orEmpty().any(::isJapaneseVoice)
        }.getOrDefault(false)
        _available.value = ready
    }

    /** Speaks [text] now, replacing whatever is being spoken. Returns false when no voice is ready. */
    fun speak(text: String): Boolean {
        val value = text.trim()
        if (!ready || value.isEmpty()) return false
        return runCatching {
            engine.speak(value, TextToSpeech.QUEUE_FLUSH, null, "jisho-lens-speech")
        }.getOrDefault(false) == TextToSpeech.SUCCESS
    }

    fun close() {
        closed = true
        ready = false
        _available.value = false
        runCatching {
            engine.stop()
            engine.shutdown()
        }
    }

    private fun isJapaneseVoice(voice: Voice): Boolean =
        voice.locale.language == Locale.JAPANESE.language && !voice.isNetworkConnectionRequired
}
