package com.elianiva.jisholens.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.Manifest
import android.os.StatFs
import android.provider.OpenableColumns
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.elianiva.jisholens.JishoLensApp
import com.elianiva.jisholens.MainActivity
import com.elianiva.jisholens.R
import com.elianiva.jisholens.dictionary.ImportCancelledException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

class ImportService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val container by lazy { (application as JishoLensApp).container }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> container.importController.requestCancel()
            ACTION_START -> {
                val uri = intent.getStringExtra(EXTRA_URI)?.let(Uri::parse)
                if (uri == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForegroundWithNotification("Preparing import", null)
                scope.launch { runImport(uri) }
            }
            ACTION_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_URL)
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "dictionary"
                if (url == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForegroundWithNotification("Preparing download", title)
                scope.launch { runDownload(url, title) }
            }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private suspend fun runImport(uri: Uri) {
        val controller = container.importController
        controller.reset()
        var cachedFile: File? = null
        try {
            val name = displayName(uri) ?: "dictionary.zip"
            val total = displaySize(uri)
            controller.update(ImportState.Copying(name, 0, total))
            cachedFile = copyToCache(uri, name, total)
            importArchive(cachedFile, name)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: ImportCancelledException) {
            controller.update(ImportState.Cancelled)
        } catch (error: Exception) {
            fail(error, "The archive could not be imported")
        } finally {
            cachedFile?.delete()
            finishSession()
        }
    }

    /**
     * One tap from onboarding: fetch the publisher's archive, then import it with the
     * same foreground session and progress as a picked file.
     */
    private suspend fun runDownload(url: String, title: String) {
        val controller = container.importController
        controller.reset()
        var downloaded: File? = null
        try {
            val directory = File(cacheDir, "downloads").apply { mkdirs() }
            val target = File(directory, "${System.currentTimeMillis()}-${title.replace(' ', '-')}.zip")
            notify("Downloading $title", "Starting")
            var notifiedStep = -1L
            downloaded = DictionaryDownloader.download(
                url = url,
                target = target,
                onProgress = { bytes, total ->
                    controller.update(ImportState.Downloading(title, bytes, total))
                    // The system rate-limits notifications; one per 2 MB is plenty.
                    val step = bytes / (2L * 1024 * 1024)
                    if (step != notifiedStep) {
                        notifiedStep = step
                        notify("Downloading $title", "${bytes / 1024 / 1024} MB")
                    }
                },
                shouldContinue = { !controller.cancelRequested },
            )
            importArchive(downloaded, title)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: ImportCancelledException) {
            controller.update(ImportState.Cancelled)
        } catch (error: Exception) {
            fail(error, "The dictionary could not be downloaded")
        } finally {
            downloaded?.delete()
            finishSession()
        }
    }

    private suspend fun importArchive(archive: File, name: String) {
        val controller = container.importController
        notify("Importing $name", "Reading archive")
        val summary = container.repository.import(
            archiveFile = archive,
            onProgress = { message ->
                controller.update(ImportState.Importing(name, message))
                notify("Importing $name", message)
            },
            shouldContinue = { !controller.cancelRequested },
        )
        controller.update(ImportState.Done(summary))
        container.refreshDictionaries()
        notify("Import complete", summary.title)
    }

    private fun fail(error: Exception, fallback: String) {
        container.importController.update(ImportState.Failed(error.message ?: fallback))
        notify("Import failed", error.message ?: fallback)
    }

    private fun finishSession() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun copyToCache(uri: Uri, name: String, total: Long?): File {
        val directory = File(cacheDir, "imports").apply { mkdirs() }
        val target = File(directory, "${System.currentTimeMillis()}-${name.substringAfterLast('/')}")
        val input = contentResolver.openInputStream(uri) ?: throw IOException("Cannot read the selected file")
        input.use { source ->
            target.outputStream().use { sink ->
                val buffer = ByteArray(64 * 1024)
                var copied = 0L
                var lastReported = 0L
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    sink.write(buffer, 0, read)
                    copied += read
                    if (copied - lastReported >= 256 * 1024) {
                        lastReported = copied
                        container.importController.update(ImportState.Copying(name, copied, total))
                    }
                }
            }
        }
        val free = StatFs(cacheDir.path).availableBytes
        val estimate = target.length() * 8
        if (free < estimate) {
            target.delete()
            throw IOException("Not enough free space. About ${estimate / 1024 / 1024} MB is needed.")
        }
        return target
    }

    private fun displayName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    private fun displaySize(uri: Uri): Long? = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
    }.getOrNull()

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.import_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.import_channel_description)
                setShowBadge(false)
            },
        )
    }

    private fun startForegroundWithNotification(message: String, detail: String?) {
        val notification = notification(message, detail)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun notify(message: String, detail: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        runCatching {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification(message, detail))
        }
    }

    private fun notification(message: String, detail: String?): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val cancel = PendingIntent.getService(
            this,
            1,
            Intent(this, ImportService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(message)
            .setContentText(detail)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .addAction(0, getString(R.string.import_notification_cancel), cancel)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "dictionary-import"
        private const val NOTIFICATION_ID = 41
        private const val ACTION_START = "com.elianiva.jisholens.action.IMPORT"
        private const val ACTION_DOWNLOAD = "com.elianiva.jisholens.action.DOWNLOAD_DICTIONARY"
        private const val ACTION_CANCEL = "com.elianiva.jisholens.action.CANCEL_IMPORT"
        private const val EXTRA_URI = "uri"
        private const val EXTRA_URL = "url"
        private const val EXTRA_TITLE = "title"

        fun start(context: Context, uri: Uri) {
            val intent = Intent(context, ImportService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_URI, uri.toString())
            ContextCompat.startForegroundService(context, intent)
        }

        /** Downloads [url] and imports it in the same foreground session. */
        fun download(context: Context, url: String, title: String) {
            val intent = Intent(context, ImportService::class.java)
                .setAction(ACTION_DOWNLOAD)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_TITLE, title)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
