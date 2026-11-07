package com.elianiva.jisholens.data

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.elianiva.jisholens.dictionary.FtsText
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class TextLineBox(
    val text: String,
    val rect: Rect,
    val elements: List<Rect>,
)

/** ML Kit Japanese text recognition, kept on a bitmap so box coordinates line up. */
class OcrEngine {
    private val client: TextRecognizer by lazy {
        TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    }

    suspend fun recognize(bitmap: Bitmap): List<TextLineBox> = suspendCancellableCoroutine { continuation ->
        client.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                continuation.resume(
                    result.textBlocks
                        .flatMap { it.lines }
                        .mapNotNull { line ->
                            val box = line.boundingBox ?: return@mapNotNull null
                            if (line.text.none { FtsText.isJapanese(it.code) }) return@mapNotNull null
                            TextLineBox(
                                text = line.text.trim(),
                                rect = box,
                                elements = line.elements.mapNotNull { it.boundingBox },
                            )
                        },
                )
            }
            .addOnFailureListener { continuation.resumeWithException(it) }
    }

    fun close() {
        client.close()
    }
}
