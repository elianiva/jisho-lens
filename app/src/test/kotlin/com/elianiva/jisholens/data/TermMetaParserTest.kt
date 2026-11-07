package com.elianiva.jisholens.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
