package com.elianiva.jisholens.dictionary

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeinflectionTest {
    @Test
    fun `deinflects an ichidan passive causative`() {
        val forms = Deinflector.default.deinflect("食べさせられた")
        val form = forms.first { it.text == "食べる" && "v1" in it.tags }

        assertEquals("食べさせられた", form.originalText)
        assertTrue(form.reasons.contains("causative"))
        assertTrue(form.reasons.any { it.contains("passive") })
    }

    @Test
    fun `deinflects a godan past, passive, and causative`() {
        val deinflector = Deinflector.default

        assertTrue(deinflector.deinflect("書いた").any { it.text == "書く" && "v5k" in it.tags })
        assertTrue(deinflector.deinflect("書かれた").any { it.text == "書く" && "v5k" in it.tags })
        assertTrue(deinflector.deinflect("書かせられた").any { it.text == "書く" })
    }

    @Test
    fun `deinflects negative and polite forms`() {
        val deinflector = Deinflector.default

        assertTrue(deinflector.deinflect("食べられなかった").any { it.text == "食べる" })
        assertTrue(deinflector.deinflect("飲まなかった").any { it.text == "飲む" })
        assertTrue(deinflector.deinflect("食べませんでした").any { it.text == "食べる" })
    }

    @Test
    fun `deinflects progressive and contracted forms`() {
        val deinflector = Deinflector.default

        assertTrue(deinflector.deinflect("食べさせられている").any { it.text == "食べる" })
        assertTrue(deinflector.deinflect("食べちゃった").any { it.text == "食べる" })
        assertTrue(deinflector.deinflect("食べてあげた").any { it.text == "食べる" })
        assertTrue(deinflector.deinflect("食べさせてあげた").any { it.text == "食べる" })
    }

    @Test
    fun `deinflection is deterministic and bounded`() {
        val deinflector = Deinflector.default
        val first = deinflector.deinflect("食べさせられた")
        val second = deinflector.deinflect("食べさせられた")

        assertEquals(first.map { it.text to it.tags }, second.map { it.text to it.tags })
        assertTrue(first.size <= 250, "expected at most 250 forms, got ${first.size}")
        assertEquals(first.map { it.text.length }.sortedDescending(), first.map { it.text.length })
    }

    @Test
    fun `lookup resolves conjugated words to their dictionary entries`() {
        withDictionary { lookup ->
            val form = lookup.lookup("食べさせられた").first { it.expression == "食べる" }
            val deinflection = assertNotNull(form.deinflection)

            assertEquals("食べさせられた", deinflection.originalText)
            assertEquals("食べる", deinflection.text)
            assertTrue(deinflection.reasons.contains("causative"))
            assertTrue(lookup.lookup("書かれた").any { it.expression == "書く" })
            assertTrue(lookup.lookup("書かせられた").any { it.expression == "書く" })
            assertTrue(lookup.lookup("飲まなかった").any { it.expression == "飲む" })
            assertTrue(lookup.lookup("高くなかった").any { it.expression == "高い" })
            assertTrue(lookup.lookup("勉強した").any { it.expression == "勉強" })
            // JMdict writes generic godan rules as "v5", not "v5r".
            assertTrue(lookup.lookup("走った").any { it.expression == "走る" })
        }
    }

    @Test
    fun `lookup handles katakana input`() {
        withDictionary { lookup ->
            val form = lookup.lookup("タベタ").first { it.expression == "食べる" }
            assertEquals("タベタ", form.deinflection?.originalText)
        }
    }

    @Test
    fun `an exact match is not reported as deinflected`() {
        withDictionary { lookup ->
            val form = lookup.lookup("食べる").first { it.expression == "食べる" }
            assertNull(form.deinflection)
        }
    }

    @Test
    fun `a noun homograph does not match a verb deinflection`() {
        withDictionary { lookup ->
            val results = lookup.lookup("食べた")

            assertTrue(results.any { it.expression == "食べる" })
            assertTrue(results.none { it.expression == "食べ" })
        }
    }

    @Test
    fun `search resolves conjugated words`() {
        withDictionary { lookup ->
            val form = lookup.search("食べさせられた").first { it.expression == "食べる" }
            assertNotNull(form.deinflection)
            assertTrue(form.deinflection.reasons.contains("passive/potential"))
        }
    }

    private fun withDictionary(block: (DictionaryLookup) -> Unit) {
        Fixtures.database().use { database ->
            Fixtures.archive(entries()).use { archive -> YomitanImporter(database).import(archive) }
            block(DictionaryLookup(database))
        }
    }

    private fun entries(): Map<String, ByteArray> = mapOf(
        "index.json" to """{"title":"Deinflection Dictionary","format":3}""".toByteArray(),
        "term_bank_1.json" to """
            [
              ["食べる","たべる","v1 vt","v1",10,["to eat"],100,"common"],
              ["食べ","たべ","n","",5,["eating"],100,""],
              ["飲む","のむ","v5m","v5m",2,["to drink"],300,""],
              ["書く","かく","v5k vt","v5k",3,["to write"],300,""],
              ["高い","たかい","adj-i","adj-i",4,["high"],300,""],
              ["勉強","べんきょう","n vs vt","",4,["study"],300,""],
              ["走る","はしる","v5r vi","v5",4,["to run"],300,""]
            ]
        """.trimIndent().toByteArray(),
    )
}
