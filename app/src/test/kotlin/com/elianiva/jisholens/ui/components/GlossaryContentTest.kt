package com.elianiva.jisholens.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.elianiva.jisholens.ui.theme.JishoLensTheme
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GlossaryContentTest {
    @get:Rule
    val compose = createComposeRule()

    /** Jitendex stores a lone sense group as a single object, not an array. */
    @Test
    fun `lists render when content is one element`() {
        val json = Json.parseToJsonElement(
            """
            {"tag":"ul","content":{"tag":"li","content":[
              {"tag":"span","content":"noun"},
              {"tag":"ol","content":{"tag":"li","content":{"tag":"ul","content":[
                {"tag":"li","content":"skin"},{"tag":"li","content":"hide"}
              ]}}}
            ]}}
            """.trimIndent(),
        )
        compose.setContent {
            JishoLensTheme(dynamicColor = false) {
                GlossaryContent(json, dictionaryId = 1L, loadMedia = { _, _ -> null }, onQuery = {})
            }
        }
        compose.onNodeWithText("noun").assertExists()
        compose.onNodeWithText("skin").assertExists()
        compose.onNodeWithText("hide").assertExists()
    }
}
