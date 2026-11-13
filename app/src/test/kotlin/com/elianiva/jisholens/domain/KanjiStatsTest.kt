package com.elianiva.jisholens.domain

import com.elianiva.jisholens.dictionary.KanjiResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KanjiStatsTest {
    private fun stats(json: String): JsonObject = Json.parseToJsonElement(json) as JsonObject

    @Test
    fun `reads the five KANJIDIC statistics`() {
        val parsed = parseKanjiStats(stats("""{"grade":1,"jlpt":4,"freq":1509,"strokes":9,"radical":"口"}"""))
        assertEquals(1, parsed.grade)
        assertEquals(4, parsed.jlpt)
        assertEquals(1509, parsed.frequency)
        assertEquals(9, parsed.strokes)
        assertEquals("口", parsed.radical)
    }

    @Test
    fun `accepts strings and the long key names`() {
        val parsed = parseKanjiStats(
            stats("""{"grade":"8","jlptLevel":"2","frequency":"412","strokeCount":"13","radicalName":"水"}"""),
        )
        assertEquals(8, parsed.grade)
        assertEquals(2, parsed.jlpt)
        assertEquals(412, parsed.frequency)
        assertEquals(13, parsed.strokes)
        assertEquals("水", parsed.radical)
    }

    @Test
    fun `missing statistics stay null`() {
        assertTrue(parseKanjiStats(stats("""{}""")).isEmpty)
        assertEquals(null, parseKanjiStats(stats("""{"strokes":"not a number"}""")).strokes)
    }

    @Test
    fun `a kanji row splits readings and tags`() {
        val detail = KanjiResult(
            kanjiId = 1,
            dictionaryId = 2,
            dictionaryTitle = "KANJIDIC",
            character = "食",
            onyomi = "ショク ジキ",
            kunyomi = "く.う,た.べる",
            tags = "jouyou freq",
            meanings = JsonArray(emptyList()),
            stats = stats("""{"strokes":9}"""),
        ).toDetail(mapOf("freq" to "Frequency"))
        assertEquals(listOf("ショク", "ジキ"), detail.onyomi)
        assertEquals(listOf("く.う", "た.べる"), detail.kunyomi)
        assertEquals(listOf("jouyou", "freq"), detail.tags.map { it.name })
        assertEquals("Frequency", detail.tags.last().notes)
        assertEquals(9, detail.stats.strokes)
    }
}
