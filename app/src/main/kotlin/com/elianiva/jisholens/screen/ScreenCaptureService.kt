package com.elianiva.jisholens.screen

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.elianiva.jisholens.JishoLensApp
import com.elianiva.jisholens.R
import com.elianiva.jisholens.ScreenScanActivity

/**
 * One reading session of screen capture.
 *
 * The service takes the consent result from [CaptureTriggerActivity], runs the
 * mandatory `mediaProjection` foreground service, and keeps a virtual display
 * alive so a rescan costs no second consent. The display writes into an
 * [ImageReader]; a frame is copied into the in-memory [com.elianiva.jisholens.data.ScreenFrameStore]
 * only when someone asks for one, then the reader opens on that frozen frame.
 *
 * Nothing is written to disk. The session stops when the reader closes, when the
 * user stops it from the notification, when the system stops the projection, or
 * after ten idle minutes.
 */
class ScreenCaptureService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val container by lazy { (application as JishoLensApp).container }

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var width = 0
    private var height = 0
    private var density = 0
    private var captureRequested = false

    private val idleStop = Runnable { stopSelf() }

    private val stopCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data = IntentCompat.getParcelableExtra(intent, EXTRA_RESULT_DATA, Intent::class.java)
                if (data == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                goForeground()
                startSession(resultCode, data)
            }
            ACTION_CAPTURE -> capture()
            ACTION_STOP -> stopSelf()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startSession(resultCode: Int, data: Intent) {
        if (projection != null) {
            capture()
            return
        }
        val manager = getSystemService(MediaProjectionManager::class.java)
        val created = manager.getMediaProjection(resultCode, data)
        if (created == null) {
            stopSelf()
            return
        }
        projection = created
        created.registerCallback(stopCallback, handler)

        val metrics = resources.displayMetrics
        width = metrics.widthPixels
        height = metrics.heightPixels
        density = metrics.densityDpi

        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, IMAGE_BUFFER_COUNT)
        imageReader.setOnImageAvailableListener({ onImageAvailable() }, handler)
        reader = imageReader

        display = created.createVirtualDisplay(
            "jisho-lens",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface,
            null,
            handler,
        )
        capture()
    }

    /** Asks for the next frame. Stale frames are dropped so the capture is current. */
    private fun capture() {
        val imageReader = reader ?: return
        captureRequested = true
        // The producer keeps two frames queued while the session is idle. Those are
        // from before this request; close them so the next one is what is on screen now.
        while (true) {
            val stale = imageReader.acquireLatestImage() ?: break
            stale.close()
        }
        scheduleIdleStop()
    }

    private fun onImageAvailable() {
        if (!captureRequested) return
        val image = reader?.acquireLatestImage() ?: return
        captureRequested = false
        val bitmap = try {
            image.toBitmap()
        } catch (_: Exception) {
            null
        } finally {
            image.close()
        }
        if (bitmap == null) return
        container.screenFrames.put(bitmap)
        scheduleIdleStop()
    }

    /**
     * Copies the image into a bitmap of the pixels the screen actually has.
     * [ImageReader] rows are padded to the stride, which is wider than the screen.
     */
    private fun Image.toBitmap(): Bitmap {
        val plane = planes.first()
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width
        val padded = width + rowPadding / pixelStride
        val bitmap = Bitmap.createBitmap(padded, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(plane.buffer)
        if (padded == width) return bitmap
        val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
        bitmap.recycle()
        return cropped
    }

    private fun scheduleIdleStop() {
        handler.removeCallbacks(idleStop)
        handler.postDelayed(idleStop, IDLE_TIMEOUT_MILLIS)
    }

    private fun goForeground() {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            } else {
                0
            },
        )
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.screen_lens_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.screen_lens_channel_description)
                setShowBadge(false)
            },
        )
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, ScreenScanActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, ScreenCaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_screen_lens)
            .setContentTitle(getString(R.string.screen_lens_notification_title))
            .setContentText(getString(R.string.screen_lens_notification_text))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.screen_lens_notification_stop), stop)
            .build()
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacks(idleStop)
        display?.release()
        display = null
        reader?.close()
        reader = null
        projection?.unregisterCallback(stopCallback)
        projection?.stop()
        projection = null
        container.screenFrames.clear()
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // A rotated screen changes the display size. The session stays valid, but the
        // next capture needs a display of the new size.
        val metrics = resources.displayMetrics
        if (width == metrics.widthPixels && height == metrics.heightPixels) return
        width = metrics.widthPixels
        height = metrics.heightPixels
        rebuildDisplay()
    }

    private fun rebuildDisplay() {
        val mediaProjection = projection ?: return
        display?.release()
        reader?.close()
        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, IMAGE_BUFFER_COUNT)
        imageReader.setOnImageAvailableListener({ onImageAvailable() }, handler)
        reader = imageReader
        display = mediaProjection.createVirtualDisplay(
            "jisho-lens",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface,
            null,
            handler,
        )
    }

    companion object {
        private const val CHANNEL_ID = "screen-lens"
        private const val NOTIFICATION_ID = 42
        private const val IMAGE_BUFFER_COUNT = 2
        private const val IDLE_TIMEOUT_MILLIS = 10 * 60 * 1000L

        private const val ACTION_START = "com.elianiva.jisholens.action.START_CAPTURE"
        private const val ACTION_CAPTURE = "com.elianiva.jisholens.action.CAPTURE"
        private const val ACTION_STOP = "com.elianiva.jisholens.action.STOP_CAPTURE"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"

        @Volatile
        private var running = false

        /** True while a consented session is alive, so a rescan needs no new consent. */
        val isRunning: Boolean get() = running

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenCaptureService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, data)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Captures another frame from the live session. Does nothing without one. */
        fun capture(context: Context) {
            if (!running) return
            context.startService(Intent(context, ScreenCaptureService::class.java).setAction(ACTION_CAPTURE))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, ScreenCaptureService::class.java).setAction(ACTION_STOP))
        }
    }
}
