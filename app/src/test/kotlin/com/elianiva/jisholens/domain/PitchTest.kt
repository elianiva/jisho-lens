package com.elianiva.jisholens.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PitchTest {
    @Test
    fun `splits a reading into morae`() {
        assertEquals(listOf("た", "べ", "る"), morae("たべる"))
        assertEquals(listOf("きゃ", "く"), morae("きゃく"))
        assertEquals(listOf("が", "っ", "こ", "う"), morae("がっこう"))
        assertEquals(listOf("こ", "ー", "ひ", "ー"), morae("コーヒー"))
        assertEquals(emptyList<String>(), morae(""))
    }

    @Test
    fun `names the accent pattern from the position`() {
        assertEquals(PitchShape.Heiban, PitchAccent("たべる", 0).shape(3))
        assertEquals(PitchShape.Odaka, PitchAccent("たべる", 3).shape(3))
        assertEquals(PitchShape.Odaka, PitchAccent("たべる", 5).shape(3))
        assertEquals(PitchShape.Atamadaka, PitchAccent("みる", 1).shape(2))
        assertEquals(PitchShape.Nakadaka, PitchAccent("たべる", 2).shape(3))
    }

    @Test
    fun `heiban rises after the first mora`() {
        val accent = PitchAccent("にほんご", 0)
        assertFalse(accent.isHigh(1, 4))
        assertTrue(accent.isHigh(2, 4))
        assertTrue(accent.isHigh(4, 4))
    }

    @Test
    fun `atamadaka drops after the first mora`() {
        val accent = PitchAccent("みる", 1)
        assertTrue(accent.isHigh(1, 2))
        assertFalse(accent.isHigh(2, 2))
    }

    @Test
    fun `nakadaka drops after the accented mora`() {
        val accent = PitchAccent("たべる", 2)
        assertFalse(accent.isHigh(1, 3))
        assertTrue(accent.isHigh(2, 3))
        assertFalse(accent.isHigh(3, 3))
    }

    @Test
    fun `odaka keeps the last mora high`() {
        val accent = PitchAccent("はな", 2)
        assertFalse(accent.isHigh(1, 2))
        assertTrue(accent.isHigh(2, 2))
    }

    @Test
    fun `labels the pattern and its position`() {
        assertEquals("heiban [0]", PitchAccent("にほんご", 0).label(4))
        assertEquals("nakadaka [2]", PitchAccent("たべる", 2).label(3))
        assertEquals("odaka [2]", PitchAccent("はな", 2).label(2))
        assertEquals("atamadaka [1]", PitchAccent("みる", 1).label(2))
    }
}
