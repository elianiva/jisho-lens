package com.elianiva.jisholens.screen

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.elianiva.jisholens.data.OcrEngine
import com.elianiva.jisholens.data.TextLineBox
import com.elianiva.jisholens.dictionary.FtsText
import com.google.mlkit.common.MlKitException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

/** What one look at the screen produced. */
sealed interface LensReading {
    data class Lines(val lines: List<LensLine>, val fromImage: Boolean) : LensReading
    data object NoText : LensReading
    data object Protected : LensReading
    data object OcrUnavailable : LensReading
    data object Failed : LensReading
}

/** Lets the reader hide the lens while a display screenshot would otherwise include it. */
interface LensOverlayVisibility {
    suspend fun <T> hidden(block: suspend () -> T): T
}

/**
 * Reads the active window, then falls back to a screenshot and ML Kit when the text
 * layer has no Japanese.
 *
 * The text path is live: it reads the accessibility tree each time, so the page never
 * needs to be frozen and no pixels leave the display. The screenshot path uses the
 * accessibility service's own screenshot API, which needs no MediaProjection consent.
 * On Android 14 and later it captures the active window only, so the lens overlay is
 * not in the image; on older versions the overlay is hidden for the capture and shown
 * again immediately.
 */
class LensNodeReader(
    private val service: AccessibilityService,
    private val ocr: OcrEngine,
    private val visibility: LensOverlayVisibility,
) {
    val canReadImages: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && runCatching {
            service.serviceInfo.capabilities and AccessibilityServiceInfo.CAPABILITY_CAN_TAKE_SCREENSHOT != 0
        }.getOrDefault(false)

    suspend fun read(forceImage: Boolean): LensReading {
        val window = activeWindow()
        val activeRoot = runCatching { window?.root ?: service.rootInActiveWindow }.getOrNull()
        if (activeRoot == null || activeRoot.packageName?.toString() == service.packageName) {
            // The lens is over Jisho Lens itself, or there is no readable window. Do not
            // screenshot our own overlay and call it the page.
            return LensReading.NoText
        }
        if (!forceImage) {
            val nodes = runCatching { readNodes(window) }.getOrDefault(emptyList())
            val lines = buildLensLines(nodes)
            if (lines.isNotEmpty()) return LensReading.Lines(lines, fromImage = false)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return if (forceImage) LensReading.OcrUnavailable else LensReading.NoText
        }
        if (!canReadImages) {
            return if (forceImage) LensReading.OcrUnavailable else LensReading.NoText
        }
        val shot = try {
            screenshot(window)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecureScreenshotException) {
            return LensReading.Protected
        } catch (_: Exception) {
            return LensReading.Failed
        }
        return try {
            if (shot.bitmap.isProtected()) return LensReading.Protected
            val boxes = try {
                ocr.recognize(shot.bitmap)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (unavailable: MlKitException) {
                // The model is installed by Play services and may still be arriving.
                if (unavailable.errorCode == MlKitException.UNAVAILABLE) {
                    return LensReading.OcrUnavailable
                }
                emptyList()
            } catch (_: Exception) {
                emptyList()
            }
            val lines = boxes.mapNotNull { it.toLensLine(shot) }
            if (lines.isEmpty()) LensReading.NoText else LensReading.Lines(lines, fromImage = true)
        } finally {
            shot.bitmap.recycle()
        }
    }

    /** Scrolls the app under the lens; the caller re-reads the tree afterwards. */
    fun scroll(forward: Boolean): Boolean {
        val window = activeWindow() ?: return false
        val root = runCatching { window.root }.getOrNull() ?: return false
        val action = if (forward) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        var best: AccessibilityNodeInfo? = null
        var bestArea = 0L
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.addLast(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++
            if (node.isVisibleToUser && node.isScrollable) {
                val bounds = Rect().also { node.getBoundsInScreen(it) }
                val area = bounds.width().toLong() * bounds.height()
                if (area > bestArea) {
                    bestArea = area
                    best = node
                }
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let { queue.addLast(it) }
            }
        }
        return best?.performAction(action) == true
    }

    private fun activeWindow(): AccessibilityWindowInfo? = runCatching {
        service.windows
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .sortedByDescending { it.layer }
            .firstOrNull { window ->
                val root = window.root ?: return@firstOrNull false
                root.packageName?.toString() != service.packageName
            }
    }.getOrNull()

    private fun readNodes(window: AccessibilityWindowInfo?): List<LensNode> {
        val root = window?.root ?: service.rootInActiveWindow ?: return emptyList()
        if (root.packageName?.toString() == service.packageName) return emptyList()
        val nodes = ArrayList<LensNode>()
        collect(root, nodes, depth = 0, extras = intArrayOf(0))
        return nodes
    }

    private fun collect(
        node: AccessibilityNodeInfo?,
        out: MutableList<LensNode>,
        depth: Int,
        extras: IntArray,
    ) {
        if (node == null || depth > MAX_DEPTH || out.size >= MAX_NODES) return
        if (!node.isPassword && node.isVisibleToUser) {
            val text = node.text?.toString().orEmpty()
            if (text.isNotBlank() && text.any { FtsText.isJapanese(it.code) }) {
                val bounds = Rect().also { node.getBoundsInScreen(it) }
                if (!bounds.isEmpty && bounds.width() > 0 && bounds.height() > 0) {
                    val characters = if (extras[0] < MAX_EXTRAS) {
                        extras[0]++
                        characterBounds(node, text)
                    } else {
                        emptyList()
                    }
                    out += LensNode(
                        text = text,
                        bounds = bounds,
                        characterBounds = characters,
                        textSizePx = textSize(node),
                    )
                }
            }
        }
        for (index in 0 until node.childCount) {
            collect(node.getChild(index), out, depth + 1, extras)
        }
    }

    @Suppress("DEPRECATION")
    private fun characterBounds(node: AccessibilityNodeInfo, text: String): List<RectF?> {
        if (text.length > MAX_TEXT_LENGTH) return emptyList()
        val args = Bundle().apply {
            putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX, 0)
            putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH, text.length)
        }
        val refreshed = runCatching {
            node.refreshWithExtraData(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY, args)
        }.getOrDefault(false)
        if (!refreshed) return emptyList()
        val array = node.extras
            .getParcelableArray(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY)
            ?: return emptyList()
        if (array.size != text.length) return emptyList()
        return array.map { it as? RectF }
    }

    private fun textSize(node: AccessibilityNodeInfo): Float? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            node.extraRenderingInfo?.textSizeInPx?.takeIf { it > 0f }
        } else {
            null
        }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun screenshot(window: AccessibilityWindowInfo?): LensShot {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && window != null) {
            val result = capture { executor, callback ->
                service.takeScreenshotOfWindow(window.id, executor, callback)
            }
            val bounds = Rect().also { window.getBoundsInScreen(it) }
            result.toShot(bounds)
        } else {
            visibility.hidden {
                val result = capture { executor, callback ->
                    service.takeScreenshot(Display.DEFAULT_DISPLAY, executor, callback)
                }
                result.toShot(service.displayBounds())
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun capture(
        call: (java.util.concurrent.Executor, AccessibilityService.TakeScreenshotCallback) -> Unit,
    ): AccessibilityService.ScreenshotResult = suspendCancellableCoroutine { continuation ->
        val callback = object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                if (continuation.isActive) continuation.resume(screenshot)
            }

            override fun onFailure(errorCode: Int) {
                if (!continuation.isActive) return
                if (errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW) {
                    continuation.resumeWithException(SecureScreenshotException())
                } else {
                    continuation.resumeWithException(ScreenshotException(errorCode))
                }
            }
        }
        call(ContextCompat.getMainExecutor(service), callback)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun AccessibilityService.ScreenshotResult.toShot(sourceBounds: Rect): LensShot {
        val buffer = hardwareBuffer
        val bitmap = try {
            Bitmap.wrapHardwareBuffer(buffer, colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
        } finally {
            buffer.close()
        }
        if (bitmap == null) throw ScreenshotException(AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR)
        val width = sourceBounds.width().takeIf { it > 0 } ?: bitmap.width
        val height = sourceBounds.height().takeIf { it > 0 } ?: bitmap.height
        return LensShot(
            bitmap = bitmap,
            origin = Point(sourceBounds.left, sourceBounds.top),
            scaleX = bitmap.width.toFloat() / width,
            scaleY = bitmap.height.toFloat() / height,
        )
    }

    private fun TextLineBox.toLensLine(shot: LensShot): LensLine? {
        if (text.isBlank() || text.none { FtsText.isJapanese(it.code) }) return null
        val left = shot.origin.x + (rect.left * shot.scaleX).roundToInt()
        val top = shot.origin.y + (rect.top * shot.scaleY).roundToInt()
        val right = shot.origin.x + (rect.right * shot.scaleX).roundToInt()
        val bottom = shot.origin.y + (rect.bottom * shot.scaleY).roundToInt()
        val characters = if (elements.size == text.length) {
            elements.map { element ->
                Rect(
                    shot.origin.x + (element.left * shot.scaleX).roundToInt(),
                    shot.origin.y + (element.top * shot.scaleY).roundToInt(),
                    shot.origin.x + (element.right * shot.scaleX).roundToInt(),
                    shot.origin.y + (element.bottom * shot.scaleY).roundToInt(),
                )
            }
        } else {
            emptyList()
        }
        return LensLine(text = text, rect = Rect(left, top, right, bottom), characters = characters)
    }

    private data class LensShot(
        val bitmap: Bitmap,
        val origin: Point,
        val scaleX: Float,
        val scaleY: Float,
    )

    private class SecureScreenshotException : Exception()

    private class ScreenshotException(val code: Int) : Exception("Screenshot failed with $code")

    private companion object {
        const val MAX_DEPTH = 40
        const val MAX_NODES = 4_000
        const val MAX_TEXT_LENGTH = 2_000
        const val MAX_EXTRAS = 240
    }
}
