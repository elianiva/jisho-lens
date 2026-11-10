package com.elianiva.jisholens.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TermMetaParserTest {
    @Test
    fun `reads numeric frequency`() {
        assertEquals(100.0, TermMetaParser.frequency("100"))
    }

    @Test
    fun `reads frequency object`() {
        assertEquals(5.0, TermMetaParser.frequency("""{"value":5,"displayValue":"five"}"""))
    }

    @Test
    fun `reads numeric string`() {
        assertEquals(3.0, TermMetaParser.frequency("\"3\""))
    }

    @Test
    fun `ignores non numeric data`() {
        assertNull(TermMetaParser.frequency("\"three\""))
        assertNull(TermMetaParser.frequency("not json"))
    }

    @Test
    fun `reads a pitch object`() {
        val accents = TermMetaParser.pitch(
            """{"reading":"たべる","pitches":[{"position":2,"tags":["devoice"]}]}""",
        )
        val accent = accents.single()
        assertEquals("たべる", accent.reading)
        assertEquals(2, accent.position)
        assertEquals(listOf("devoice"), accent.tags)
    }

    @Test
    fun `reads a bare pitch array`() {
        val accent = TermMetaParser.pitch("""[{"position":0}]""").single()
        assertEquals("", accent.reading)
        assertEquals(0, accent.position)
        assertTrue(accent.tags.isEmpty())
    }

    @Test
    fun `reads every pitch of a word`() {
        val accents = TermMetaParser.pitch(
            """{"reading":"はし","pitches":[{"position":1},{"position":3}]}""",
        )
        assertEquals(listOf(1, 3), accents.map { it.position })
    }

    @Test
    fun `ignores pitch data that is not a position`() {
        assertTrue(TermMetaParser.pitch("""{"reading":"はし","pitches":[{"tags":[]}]}""").isEmpty())
        assertTrue(TermMetaParser.pitch("not json").isEmpty())
        assertTrue(TermMetaParser.pitch("null").isEmpty())
    }
}
