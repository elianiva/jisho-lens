package com.elianiva.jisholens.data

import com.elianiva.jisholens.domain.Entry
import com.elianiva.jisholens.domain.ScanWord
import kotlinx.serialization.json.JsonArray
import org.junit.Assert.assertEquals
import org.junit.Test

class CandidateRankingTest {
    private fun entry(id: Long, frequency: Double?, score: Double = 0.0) = Entry(
        termId = id,
        dictionaryId = 1,
        dictionaryTitle = "Test",
        expression = "語$id",
        reading = "ご",
        ruby = emptyList(),
        tags = emptyList(),
        glossary = JsonArray(emptyList()),
        frequency = frequency,
        score = score,
        deinflection = null,
    )

    private fun word(
        surface: String,
        frequency: Double?,
        exact: Boolean = true,
        score: Double = 0.0,
    ) = ScanWord(
        surface = surface,
        start = 0,
        end = surface.length,
        entries = listOf(entry(surface.hashCode().toLong(), frequency, score)),
        exact = exact,
    )

    @Test
    fun `ranked entries put the most common first and unknown ranks last`() {
        val ranked = rankEntries(listOf(entry(1, null), entry(2, 500.0), entry(3, 20.0)))
        assertEquals(listOf(3L, 2L, 1L), ranked.map { it.termId })
    }

    @Test
    fun `an unranked entry loses to a ranked one with the same score`() {
        val ranked = rankEntries(listOf(entry(1, null, score = 9.0), entry(2, 900.0, score = 1.0)))
        assertEquals(listOf(2L, 1L), ranked.map { it.termId })
    }

    @Test
    fun `an exact headword beats a more common conjugated form`() {
        val ranked = rankCandidates(
            listOf(
                word("食べさせられた", frequency = 10.0, exact = false),
                word("食べ", frequency = 9000.0),
                word("食べる", frequency = 50.0),
            ),
        )
        assertEquals(listOf("食べる", "食べ", "食べさせられた"), ranked.map { it.surface })
    }

    @Test
    fun `without frequency the longer exact surface stays first`() {
        val ranked = rankCandidates(listOf(word("日本", null), word("日本語", null)))
        assertEquals(listOf("日本語", "日本"), ranked.map { it.surface })
    }

    @Test
    fun `the more common word wins at equal length`() {
        val ranked = rankCandidates(listOf(word("a語", 400.0), word("b語", 30.0)))
        assertEquals(listOf("b語", "a語"), ranked.map { it.surface })
    }

    @Test
    fun `ranking a candidate also ranks its entries`() {
        val word = ScanWord(
            surface = "語",
            start = 0,
            end = 1,
            entries = listOf(entry(1, 900.0), entry(2, 30.0)),
        )
        assertEquals(listOf(2L, 1L), rankCandidates(listOf(word)).single().entries.map { it.termId })
    }
}
