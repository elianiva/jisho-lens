package com.elianiva.jisholens.screen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class BitmapProtectionTest {
    @Test
    fun `a flat dark frame is treated as a protected window`() {
        val black = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        assertTrue(black.isProtected())
    }

    @Test
    fun `a page with edges is not protected`() {
        val page = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(page)
        canvas.drawColor(Color.WHITE)
        canvas.drawRect(8f, 8f, 40f, 24f, Paint().apply { color = Color.BLACK })
        assertFalse(page.isProtected())
    }

    @Test
    fun `a flat light frame is empty but not protected`() {
        val white = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        white.eraseColor(Color.WHITE)
        assertFalse(white.isProtected())
    }
}
