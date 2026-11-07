package com.elianiva.jisholens.dictionary

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FtsTextTest {
    @Test
    fun `segments japanese into one token per character`() {
        assertEquals("食 べ る ", FtsText.indexText("食べる"))
    }

    @Test
    fun `keeps latin text untouched`() {
        assertEquals("to eat", FtsText.indexText("to eat"))
        assertEquals("", FtsText.indexText(""))
    }

    @Test
    fun `splits mixed text around japanese runs`() {
        assertEquals("食 べ food", FtsText.indexText("食べfood"))
    }

    @Test
    fun `normalizes halfwidth katakana`() {
        assertEquals("カ タ カ ナ ", FtsText.indexText("ｶﾀｶﾅ"))
    }

    @Test
    fun `builds a phrase query for japanese input`() {
        assertEquals("\"食 べ る\"", FtsText.buildQuery("食べる"))
    }

    @Test
    fun `builds prefix terms for latin input`() {
        assertEquals("to* AND eat*", FtsText.buildQuery("to eat"))
    }

    @Test
    fun `builds a mixed query`() {
        assertEquals("\"食 べ\" AND food*", FtsText.buildQuery("食べ food"))
    }

    @Test
    fun `returns null when there is nothing to search`() {
        assertNull(FtsText.buildQuery(""))
        assertNull(FtsText.buildQuery("   "))
        assertNull(FtsText.buildQuery("!!!"))
    }
}

class KanaTest {
    @Test
    fun `converts katakana to hiragana`() {
        assertEquals("たべる", Kana.toHiragana("タベル"))
        assertEquals("たべる", Kana.toHiragana("たべる"))
        assertEquals("にほんご", Kana.toHiragana("ニホンゴ"))
    }

    @Test
    fun `converts hiragana to katakana`() {
        assertEquals("タベル", Kana.toKatakana("たべる"))
        assertEquals("食ベル", Kana.toKatakana("食べる"))
    }
}
