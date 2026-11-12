package com.elianiva.jisholens.screen

import android.graphics.Rect
import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LensTextTest {
    @Test
    fun `a single line gets one box per character and a word box`() {
        val text = "ラーメンを食べた。"
        val lines = buildLensLines(listOf(LensNode(text, Rect(100, 200, 900, 280))))
        assertEquals(1, lines.size)
        val line = lines.single()
        assertEquals(text, line.text)
        assertEquals(text.length, line.characters.size)
        assertEquals(0, line.characterAt(110f, 240f))
        val food = text.indexOf('食')
        assertEquals(food, line.characterAt(100f + 800f * (food + 0.5f) / text.length, 240f))
        val word = line.wordRect(food, text.length)
        assertEquals(line.characters[food].left, word.left)
        assertEquals(line.characters.last().right, word.right)
    }

    @Test
    fun `a wrapped block with a text size splits at the width`() {
        val text = "あ".repeat(20)
        val lines = buildLensLines(
            listOf(LensNode(text, Rect(0, 0, 400, 200), textSizePx = 40f)),
        )
        assertEquals(2, lines.size)
        assertEquals("あ".repeat(10), lines[0].text)
        assertEquals("あ".repeat(10), lines[1].text)
        assertTrue(lines[0].rect.bottom <= lines[1].rect.top)
        val second = lines[1]
        assertEquals(0, second.characterAt(1f, second.rect.centerY().toFloat()))
        assertEquals(9, second.characterAt(399f, second.rect.centerY().toFloat()))
    }

    @Test
    fun `real character boxes split a paragraph into lines`() {
        val first = "昨日、友達と"
        val second = "ラーメンを食べた。"
        val text = "$first\n$second"
        val boxes = ArrayList<RectF?>()
        first.forEachIndexed { index, _ ->
            boxes += RectF(index * 60f, 100f, (index + 1) * 60f, 160f)
        }
        boxes += null
        second.forEachIndexed { index, _ ->
            boxes += RectF(index * 60f, 200f, (index + 1) * 60f, 260f)
        }
        val lines = buildLensLines(listOf(LensNode(text, Rect(0, 100, 900, 260), boxes)))
        assertEquals(2, lines.size)
        assertEquals(first, lines[0].text)
        assertEquals(second, lines[1].text)
        assertEquals(first.length, lines[0].characters.size)
        assertEquals(second.length, lines[1].characters.size)
        val food = second.indexOf('食')
        assertEquals(food, lines[1].characterAt(60f * food + 10f, 230f))
        val word = lines[1].wordRect(food, second.length)
        assertEquals(60 * food, word.left)
        assertEquals(60 * second.length, word.right)
    }

    @Test
    fun `a parent node wins over a child that repeats part of it`() {
        val lines = buildLensLines(
            listOf(
                LensNode("ラーメンを食べた。", Rect(0, 0, 500, 100)),
                LensNode("食べた", Rect(300, 0, 500, 100)),
            ),
        )
        assertEquals(1, lines.size)
        assertEquals("ラーメンを食べた。", lines.single().text)
    }

    @Test
    fun `separate text nodes on one line are both kept`() {
        val lines = buildLensLines(
            listOf(
                LensNode("ラーメン", Rect(0, 0, 200, 100)),
                LensNode("食べた", Rect(300, 0, 500, 100)),
            ),
        )
        assertEquals(2, lines.size)
    }

    @Test
    fun `a tap between lines still picks the nearest line and a far tap picks none`() {
        val lines = buildLensLines(
            listOf(
                LensNode("日本語を読む", Rect(0, 100, 500, 160)),
                LensNode("楽しいですね", Rect(0, 300, 500, 360)),
            ),
        )
        assertEquals(0, lensLineAt(lines, 100f, 150f))
        assertEquals(0, lensLineAt(lines, 100f, 190f))
        assertEquals(1, lensLineAt(lines, 100f, 350f))
        assertEquals(-1, lensLineAt(lines, 100f, 800f))
    }

    @Test
    fun `a tap on a space moves to the nearest word character`() {
        val line = LensLine("食べた ラーメン", Rect(0, 0, 140, 20))
        val space = line.text.indexOf(' ')
        assertNotEquals(space, line.nearestWordOffset(space))
        assertTrue(!line.text[line.nearestWordOffset(space)].isWhitespace())
    }
}
