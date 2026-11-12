package com.elianiva.jisholens.data

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.moduleinstall.InstallStatusListener
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The Japanese text recognition model. It lives in Google Play services rather
 * than the APK, which keeps the 41 MB of OCR native code out of the download.
 */
class OcrModelComponent(private val context: Context) : Component {
    override val id = "ocr-japanese"
    override val kind = ComponentKind.ENGINE
    override val title = "Japanese text recognition"

    private val recognizer: TextRecognizer by lazy {
        TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    }

    override suspend fun isInstalled(): Boolean =
        ModuleInstall.getClient(context).areModulesAvailable(recognizer).await().areModulesAvailable()

    override suspend fun isSupported(): Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    override suspend fun install(onProgress: (Int?) -> Unit): Boolean =
        suspendCancellableCoroutine { continuation ->
            val installer = ModuleInstall.getClient(context)
            val listener = InstallStatusListener { update ->
                when (update.installState) {
                    ModuleInstallStatusUpdate.InstallState.STATE_DOWNLOADING,
                    ModuleInstallStatusUpdate.InstallState.STATE_INSTALLING,
                    -> onProgress(update.progressInfo.percent())

                    ModuleInstallStatusUpdate.InstallState.STATE_COMPLETED ->
                        if (continuation.isActive) continuation.resume(true)

                    ModuleInstallStatusUpdate.InstallState.STATE_FAILED,
                    ModuleInstallStatusUpdate.InstallState.STATE_CANCELED,
                    -> if (continuation.isActive) continuation.resume(false)
                }
            }
            val request = ModuleInstallRequest.newBuilder()
                .addApi(recognizer)
                .setListener(listener)
                .build()
            installer.installModules(request)
                .addOnFailureListener { if (continuation.isActive) continuation.resume(false) }
            continuation.invokeOnCancellation { installer.unregisterListener(listener) }
        }
}

private fun ModuleInstallStatusUpdate.ProgressInfo?.percent(): Int? {
    if (this == null || totalBytesToDownload <= 0) return null
    return ((bytesDownloaded * 100) / totalBytesToDownload).toInt().coerceIn(0, 100)
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
}
