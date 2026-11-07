package com.elianiva.jisholens.dictionary

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FuriganaTest {
    @Test
    fun `aligns okurigana`() {
        val segments = Furigana.align("食べる", "たべる")
        assertEquals("食べる", segments.joinToString("") { it.text })
        assertEquals(listOf("食" to "た", "べ" to null, "る" to null), segments.map { it.text to it.reading })
    }

    @Test
    fun `aligns a whole kanji compound`() {
        assertEquals(listOf(RubySegment("今日", "きょう")), Furigana.align("今日", "きょう"))
        assertEquals(listOf(RubySegment("大人", "おとな")), Furigana.align("大人", "おとな"))
    }

    @Test
    fun `keeps leading kana separate`() {
        val segments = Furigana.align("お茶", "おちゃ")
        assertEquals(listOf(RubySegment("お", null), RubySegment("茶", "ちゃ")), segments)
    }

    @Test
    fun `aligns multiple kanji runs`() {
        val segments = Furigana.align("可愛い", "かわいい")
        assertEquals("可愛い", segments.joinToString("") { it.text })
        assertEquals(listOf("可愛" to "かわい", "い" to null), segments.map { it.text to it.reading })
    }

    @Test
    fun `falls back when the reading does not fit`() {
        val segments = Furigana.align("食べる", "たべません")
        assertEquals(listOf(RubySegment("食べる", "たべません")), segments)
    }

    @Test
    fun `does not add ruby to kana only words`() {
        assertNull(Furigana.align("たべる", "たべる").single().reading)
    }
}
