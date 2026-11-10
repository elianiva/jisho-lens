package com.elianiva.jisholens.domain

import kotlinx.serialization.json.JsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverageTest {
    private fun entry(expression: String, termId: Long = expression.hashCode().toLong()) = Entry(
        termId = termId,
        dictionaryId = 1,
        dictionaryTitle = "Test",
        expression = expression,
        reading = "よみ",
        ruby = emptyList(),
        tags = emptyList(),
        glossary = JsonArray(emptyList()),
        frequency = null,
        score = 0.0,
        deinflection = null,
    )

    private fun word(surface: String, vararg expressions: String) = ScanWord(
        surface = surface,
        start = 0,
        end = surface.length,
        entries = expressions.map { entry(it) },
    )

    @Test
    fun `counts unique headwords, not surfaces`() {
        val words = listOf(
            word("食べた", "食べる"),
            word("食べさせられた", "食べる"),
            word("ラーメン", "ラーメン"),
        )
        assertEquals(Coverage(known = 0, total = 2), pageCoverage(words, emptySet()))
    }

    @Test
    fun `counts a known headword once`() {
        val words = listOf(
            word("食べた", "食べる"),
            word("ラーメン", "ラーメン"),
        )
        val coverage = pageCoverage(words, setOf("食べる"))
        assertEquals(1, coverage.known)
        assertEquals(2, coverage.total)
        assertEquals(50, coverage.percent)
    }

    @Test
    fun `ignores words without entries`() {
        val coverage = pageCoverage(listOf(word("を"), word("ラーメン", "ラーメン")), setOf("ラーメン"))
        assertEquals(Coverage(known = 1, total = 1), coverage)
        assertEquals(100, coverage.percent)
    }

    @Test
    fun `an empty page has no percentage`() {
        assertEquals(0, pageCoverage(emptyList(), setOf("食べる")).percent)
    }

    @Test
    fun `a word is known when any of its entries is`() {
        assertTrue(word("食べた", "食べる", "食う").isKnown(setOf("食う")))
        assertFalse(word("食べた", "食べる").isKnown(setOf("食う")))
        assertEquals("食べる", word("食べた", "食べる", "食う").headword())
    }
}
