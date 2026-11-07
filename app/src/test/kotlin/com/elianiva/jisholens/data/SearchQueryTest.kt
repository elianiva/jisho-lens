package com.elianiva.jisholens.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchQueryTest {
    @Test
    fun `japanese uses exact lookup by default`() {
        assertFalse(SearchQuery.useFullText("食べる", false))
        assertFalse(SearchQuery.useFullText("たべる", false))
    }

    @Test
    fun `japanese uses full text when the setting is on`() {
        assertTrue(SearchQuery.useFullText("食べ", true))
    }

    @Test
    fun `english always uses full text`() {
        assertTrue(SearchQuery.useFullText("eat", false))
        assertTrue(SearchQuery.useFullText("to eat", false))
    }

    @Test
    fun `mixed text follows the latin path`() {
        assertTrue(SearchQuery.useFullText("食べる eat", false))
    }
}
