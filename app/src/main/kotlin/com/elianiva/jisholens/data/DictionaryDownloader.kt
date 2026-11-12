package com.elianiva.jisholens.data

import com.elianiva.jisholens.dictionary.ImportCancelledException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** One publisher archive to fetch and import, with the title the reader sees. */
data class DictionaryDownload(val url: String, val title: String)

/**
 * Fetches one dictionary archive into [target] and reports progress.
 *
 * The download goes straight to the publisher's release URL with the platform HTTP
 * client; there is no dependency and no proxy. A partial file is never left behind:
 * the bytes land in a temporary file that is renamed only on success, and any failure
 * deletes both.
 */
object DictionaryDownloader {
    private const val CONNECT_TIMEOUT_MILLIS = 20_000
    private const val READ_TIMEOUT_MILLIS = 60_000
    private const val REPORT_EVERY_BYTES = 512L * 1024

    suspend fun download(
        url: String,
        target: File,
        onProgress: (bytes: Long, total: Long?) -> Unit = { _, _ -> },
        shouldContinue: () -> Boolean = { true },
    ): File = withContext(Dispatchers.IO) {
        val temporary = File(target.parentFile, "${target.name}.part")
        temporary.parentFile?.mkdirs()
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = CONNECT_TIMEOUT_MILLIS
                readTimeout = READ_TIMEOUT_MILLIS
                setRequestProperty("User-Agent", "Jisho Lens")
                setRequestProperty("Accept", "application/zip, application/octet-stream")
            }
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("The dictionary server answered HTTP $code")
            val total = connection.contentLengthLong.takeIf { it > 0 }

            var copied = 0L
            var reported = 0L
            onProgress(0, total)
            connection.inputStream.use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (!shouldContinue()) throw ImportCancelledException()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (copied - reported >= REPORT_EVERY_BYTES) {
                            reported = copied
                            onProgress(copied, total)
                        }
                    }
                }
            }
            onProgress(copied, total ?: copied)
            if (target.exists()) target.delete()
            if (!temporary.renameTo(target)) throw IOException("The downloaded dictionary could not be saved")
            target
        } catch (error: Throwable) {
            temporary.delete()
            target.delete()
            throw error
        } finally {
            connection?.disconnect()
        }
    }
}
