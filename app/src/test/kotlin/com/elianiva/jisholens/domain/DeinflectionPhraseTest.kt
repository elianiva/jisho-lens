package com.elianiva.jisholens.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeinflectionPhraseTest {
    @Test
    fun `causative passive past reads as was made to eat`() {
        val phrase = deinflectionPhrase(
            reasons = listOf("past", "(unstressed infinitive)", "passive/potential", "causative"),
            gloss = "to eat; food",
        )
        assertEquals(listOf("causative", "passive or potential", "past"), phrase?.steps)
        assertEquals("was made to eat", phrase?.meaning)
    }

    @Test
    fun `a plain passive past is was eaten`() {
        val phrase = deinflectionPhrase(listOf("past", "passive"), "to eat")
        assertEquals(listOf("passive", "past"), phrase?.steps)
        assertEquals("was eaten", phrase?.meaning)
    }

    @Test
    fun `a causative without tense keeps the present`() {
        assertEquals("make (someone) eat", deinflectionPhrase(listOf("causative"), "to eat")?.meaning)
    }

    @Test
    fun `a past is one English verb`() {
        assertEquals("ate", deinflectionPhrase(listOf("past"), "to eat")?.meaning)
    }

    @Test
    fun `a negative past is did not eat`() {
        assertEquals("did not eat", deinflectionPhrase(listOf("past", "negative"), "to eat")?.meaning)
    }

    @Test
    fun `polite is part of the chain but not the phrase`() {
        val phrase = deinflectionPhrase(listOf("past", "polite"), "to eat")
        assertEquals(listOf("polite", "past"), phrase?.steps)
        assertEquals("ate", phrase?.meaning)
    }

    @Test
    fun `the first sense loses to and its alternatives`() {
        val phrase = deinflectionPhrase(
            listOf("past", "passive", "causative"),
            "to live on (e.g. a salary), to live off, to subsist on",
        )
        assertEquals("was made to live on", phrase?.meaning)
    }

    @Test
    fun `structural steps alone have no chain`() {
        assertNull(deinflectionPhrase(listOf("(stem)", "('a' stem)"), "to eat"))
        assertNull(deinflectionPhrase(emptyList(), "to eat"))
    }

    @Test
    fun `an unknown reason stays visible`() {
        val phrase = deinflectionPhrase(listOf("slang"), "to eat")
        assertEquals(listOf("slang"), phrase?.steps)
        assertEquals("eat", phrase?.meaning)
    }

    @Test
    fun `a missing gloss still gives the chain`() {
        val phrase = deinflectionPhrase(listOf("past"), null)
        assertEquals(listOf("past"), phrase?.steps)
        assertNull(phrase?.meaning)
    }
}
