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

    /**
     * The screenshot the open lens is frozen on. Recognition reads it instead of taking a
     * second capture, which would flicker the lens on Android 13 and older and could show
     * a page that has moved since the freeze. Set by the overlay host; the host owns it.
     */
    @Volatile
    var frozenPage: Bitmap? = null

    /**
     * Reads the page. [fillGaps] adds text the accessibility tree does not expose: after
     * the tree is read, the screenshot is recognized too and any line that lands where
     * the tree found nothing is added. Apps often draw part of a page as one merged or
     * custom-rendered node, so the tree alone misses lines that are plainly on screen.
     */
    suspend fun read(
        forceImage: Boolean,
        fillGaps: Boolean = true,
        onTreeLines: (List<LensLine>) -> Unit = {},
    ): LensReading {
        val window = activeWindow()
        val activeRoot = runCatching { window?.root ?: service.rootInActiveWindow }.getOrNull()
        if (activeRoot == null || activeRoot.packageName?.toString() == service.packageName) {
            // The lens is over Jisho Lens itself, or there is no readable window. Do not
            // screenshot our own overlay and call it the page.
            return LensReading.NoText
        }
        var treeLines = emptyList<LensLine>()
        if (!forceImage) {
            val nodes = runCatching { readNodes(window) }.getOrDefault(emptyList())
            treeLines = buildLensLines(nodes)
            if (treeLines.isNotEmpty() && !(fillGaps && canReadImages)) {
                return LensReading.Lines(treeLines, fromImage = false)
            }
            // Show what the tree gave now; recognition can take a second.
            if (treeLines.isNotEmpty()) onTreeLines(treeLines)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || !canReadImages) {
            return when {
                treeLines.isNotEmpty() -> LensReading.Lines(treeLines, fromImage = false)
                forceImage -> LensReading.OcrUnavailable
                else -> LensReading.NoText
            }
        }
        return when (val image = readImage(window)) {
            is ImageResult.Lines -> if (treeLines.isEmpty()) {
                if (image.lines.isEmpty()) LensReading.NoText else LensReading.Lines(image.lines, fromImage = true)
            } else {
                LensReading.Lines(mergeOcrGaps(treeLines, image.lines), fromImage = false)
            }
            is ImageResult.Failed -> if (treeLines.isNotEmpty()) {
                LensReading.Lines(treeLines, fromImage = false)
            } else {
                image.reading
            }
        }
    }

    private sealed interface ImageResult {
        data class Lines(val lines: List<LensLine>) : ImageResult
        data class Failed(val reading: LensReading) : ImageResult
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun readImage(window: AccessibilityWindowInfo?): ImageResult {
        val frozen = frozenPage?.takeUnless { it.isRecycled }
        val shot = if (frozen != null) {
            val bounds = service.displayBounds()
            LensShot(
                bitmap = frozen,
                origin = Point(bounds.left, bounds.top),
                scaleX = frozen.width.toFloat() / bounds.width().coerceAtLeast(1),
                scaleY = frozen.height.toFloat() / bounds.height().coerceAtLeast(1),
            )
        } else try {
            screenshot(window)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecureScreenshotException) {
            return ImageResult.Failed(LensReading.Protected)
        } catch (_: Exception) {
            return ImageResult.Failed(LensReading.Failed)
        }
        return try {
            if (shot.bitmap.isProtected()) return ImageResult.Failed(LensReading.Protected)
            val boxes = try {
                ocr.recognize(shot.bitmap)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (unavailable: MlKitException) {
                // The model is installed by Play services and may still be arriving.
                if (unavailable.errorCode == MlKitException.UNAVAILABLE) {
                    return ImageResult.Failed(LensReading.OcrUnavailable)
                }
                emptyList()
            } catch (_: Exception) {
                emptyList()
            }
            ImageResult.Lines(boxes.mapNotNull { it.toLensLine(shot) })
        } finally {
            // The host recycles the frozen page itself when the lens closes.
            if (frozen == null) shot.bitmap.recycle()
        }
    }

    /**
     * A screenshot of the whole display, taken before the overlay exists so the lens can
     * show it frozen. Null when the device cannot capture, the window is protected, or
     * the capture fails; the lens then stays live over the page.
     */
    suspend fun freeze(): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || !canReadImages) return null
        return try {
            val result = capture { executor, callback ->
                service.takeScreenshot(Display.DEFAULT_DISPLAY, executor, callback)
            }
            val bitmap = result.toShot(service.displayBounds()).bitmap
            if (bitmap.isProtected()) {
                bitmap.recycle()
                null
            } else {
                bitmap
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
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
            // Many apps put the readable text in the description, not in the text.
            val text = node.text?.toString()?.takeIf { it.isNotBlank() }
                ?: node.contentDescription?.toString().orEmpty()
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
