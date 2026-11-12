package com.elianiva.jisholens.data

import com.elianiva.jisholens.dictionary.JapaneseTokenizer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Kuromoji reads its IPADIC model from Java resources, so this checks that the
 * model loads and tokenizes under the Android runtime, not only on a desktop JVM.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class JapaneseTokenizerAndroidTest {
    @Test
    fun `the IPADIC model loads and tokenizes`() {
        val tokens = JapaneseTokenizer.shared.tokenize("東京都で開催される")
        assertEquals(listOf("東京", "都", "で", "開催", "さ", "れる"), tokens.map { it.surface })
        assertEquals("東京都", tokens.subList(0, 2).joinToString("") { it.surface })
        assertEquals("開催", tokens[3].baseForm)
        assertEquals("トウキョウ", tokens[0].reading)
    }
}
