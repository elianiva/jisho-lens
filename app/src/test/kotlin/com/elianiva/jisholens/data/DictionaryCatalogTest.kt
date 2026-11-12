package com.elianiva.jisholens.data

import com.elianiva.jisholens.dictionary.DictionaryRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class DictionaryCatalogTest {
    @Test
    fun `every section has an entry`() {
        assertEquals(DictionaryGroup.entries, DictionaryCatalog.groups.map { it.first })
        for (group in DictionaryCatalog.groups) {
            assertTrue("${group.first.label} is empty", group.second.isNotEmpty())
        }
    }

    @Test
    fun `every entry records its licence and terms`() {
        for (source in DictionaryCatalog.sources) {
            assertTrue(source.id.isNotBlank())
            assertTrue(source.title.isNotBlank())
            assertTrue(source.titleMatch.isNotBlank())
            assertTrue(source.description.isNotBlank())
            assertTrue(source.license.isNotBlank())
            assertTrue(source.terms.isNotBlank())
            assertTrue(source.sizeBytes > 0)
            assertTrue(source.pageUrl.startsWith("https://"))
            source.url?.let { assertTrue(it.startsWith("https://")) }
        }
        assertEquals(DictionaryCatalog.sources.size, DictionaryCatalog.sources.map { it.id }.toSet().size)
    }

    @Test
    fun `one tap downloads come from the publisher`() {
        val publishers = setOf("github.com", "api.jiten.moe")
        for (source in DictionaryCatalog.sources.filter { it.url != null }) {
            val host = URI(source.url).host
            assertTrue("${source.id} downloads from $host", host in publishers)
        }
    }

    @Test
    fun `entries whose terms forbid a direct download have no url`() {
        assertNull(DictionaryCatalog.byId("bccwj").url)
        assertNull(DictionaryCatalog.byId("jpdb").url)
        assertEquals(
            listOf("bccwj", "jpdb"),
            DictionaryCatalog.sources.filter { it.url == null }.map { it.id },
        )
    }

    @Test
    fun `the starter set covers terms kanji frequency and pitch`() {
        assertEquals(
            setOf(
                DictionaryKind.TERM,
                DictionaryKind.KANJI,
                DictionaryKind.FREQUENCY,
                DictionaryKind.PITCH,
            ),
            DictionaryCatalog.starterSet.map { it.kind }.toSet(),
        )
        assertTrue(DictionaryCatalog.starterSet.all { it.url != null })
    }

    @Test
    fun `matches an imported archive by its title`() {
        assertEquals("Jitendex", DictionaryCatalog.find("Jitendex.org [2026-10-03]")?.title)
        assertEquals("JMdict", DictionaryCatalog.find("JMdict [2026-11-11]")?.title)
        assertEquals("JMnedict", DictionaryCatalog.find("JMnedict [2026-11-11]")?.title)
        assertEquals("KANJIDIC", DictionaryCatalog.find("KANJIDIC [2026-315]")?.title)
        assertEquals("Jiten", DictionaryCatalog.find("Jiten")?.title)
        assertEquals("Jiten (Kanji)", DictionaryCatalog.find("Jiten (Kanji)")?.title)
        assertEquals("Wadoku Pitch Accent", DictionaryCatalog.find("Wadoku Pitch Accent")?.title)
        assertEquals("JA Wikipedia", DictionaryCatalog.find("JA Wikipedia [2022-12-01] (v1.6.1)")?.titleMatch)
        assertNull(DictionaryCatalog.find("Some dictionary from a file"))
    }

    @Test
    fun `reads the kind of an imported dictionary`() {
        assertEquals(DictionaryKind.TERM, DictionaryCatalog.kindOf(record(counts = mapOf("terms" to 10))))
        assertEquals(DictionaryKind.KANJI, DictionaryCatalog.kindOf(record(counts = mapOf("kanji" to 10))))
        assertEquals(
            DictionaryKind.FREQUENCY,
            DictionaryCatalog.kindOf(record(counts = mapOf("termMeta" to 10), modes = setOf("freq"))),
        )
        assertEquals(
            DictionaryKind.PITCH,
            DictionaryCatalog.kindOf(record(counts = mapOf("termMeta" to 10), modes = setOf("pitch"))),
        )
        assertEquals(
            DictionaryKind.FREQUENCY,
            DictionaryCatalog.kindOf(record(counts = mapOf("kanjiMeta" to 10))),
        )
        assertNull(DictionaryCatalog.kindOf(record(counts = emptyMap())))
    }

    @Test
    fun `a catalogue title wins over the stored counts`() {
        val names = record(title = "JMnedict [2026-11-11]", counts = mapOf("terms" to 668_032))
        assertEquals(DictionaryKind.NAMES, DictionaryCatalog.kindOf(names))
    }

    private fun record(
        title: String = "Custom",
        counts: Map<String, Int>,
        modes: Set<String> = emptySet(),
    ) = DictionaryRecord(
        id = 1,
        title = title,
        revision = "1",
        formatVersion = 3,
        sequenced = false,
        enabled = true,
        priority = 0,
        alias = null,
        importDate = 0,
        importSuccess = true,
        prefixWildcardsSupported = false,
        counts = counts,
        termMetaModes = modes,
    )
}
