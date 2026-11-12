package com.elianiva.jisholens.ui.screens

import android.graphics.Rect
import com.elianiva.jisholens.data.TextLineBox
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScanTapTest {
    private val first = TextLineBox("昨日、友達と", Rect(10, 100, 300, 160), emptyList())
    private val second = TextLineBox("ラーメンを食べた。", Rect(10, 200, 320, 260), emptyList())
    private val third = TextLineBox("とても美味しかった。", Rect(10, 300, 320, 360), emptyList())
    private val lines = listOf(first, second, third)

    @Test
    fun `a tap inside a line selects it`() {
        assertEquals(1, lineAt(lines, 100f, 230f))
    }

    @Test
    fun `a tap between lines still selects the line it meant`() {
        // Twenty pixels below the second line is inside one line height of it.
        assertEquals(1, lineAt(lines, 100f, 275f))
    }

    @Test
    fun `a tap far from every line selects nothing`() {
        assertEquals(-1, lineAt(lines, 100f, 800f))
        assertEquals(-1, lineAt(emptyList(), 100f, 100f))
    }

    @Test
    fun `a tap on the line estimates the character from the box`() {
        assertEquals(0, charIndexAt(first, 12f))
        assertEquals(3, charIndexAt(first, 170f))
        assertEquals(first.text.lastIndex, charIndexAt(first, 299f))
        assertEquals(0, charIndexAt(first, null))
    }
}
