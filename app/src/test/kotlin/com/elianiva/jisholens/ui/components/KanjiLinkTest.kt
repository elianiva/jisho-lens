package com.elianiva.jisholens.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.elianiva.jisholens.dictionary.RubySegment
import com.elianiva.jisholens.ui.theme.JishoLensTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A kanji in the headword is a link to its own screen, not just styled text. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class KanjiLinkTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `tapping a kanji in the headword reports that character`() {
        var tapped: Char? = null
        compose.setContent {
            JishoLensTheme(dynamicColor = false) {
                RubyText(
                    segments = listOf(RubySegment("食", "た"), RubySegment("べる", null)),
                    onKanji = { tapped = it },
                )
            }
        }
        compose.onNodeWithText("食").performClick()
        assertEquals('食', tapped)
    }
}
