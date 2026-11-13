package com.elianiva.jisholens.ui.screens

import android.graphics.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.IntSize
import com.elianiva.jisholens.screen.LensLine
import com.elianiva.jisholens.screen.LensUiState
import com.elianiva.jisholens.ui.theme.JishoLensTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class LensOverlayTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a swipe scrolls the page and a tap selects a word`() {
        var scrolled: Boolean? = null
        var tapped: Pair<Float, Float>? = null
        compose.setContent {
            JishoLensTheme(dynamicColor = false) {
                LensOverlayContent(
                    state = LensUiState(
                        lines = listOf(LensLine("日本語を読む", Rect(100, 600, 900, 900))),
                        scanning = false,
                    ),
                    screenSize = IntSize(1000, 2000),
                    loadMedia = { _, _ -> null },
                    onTap = { x, y -> tapped = x to y },
                    onSelectCandidate = {},
                    onToggleKnown = {},
                    onExpand = {},
                    onCollapse = {},
                    onClearSelection = {},
                    onRescan = {},
                    onReadImage = {},
                    onScroll = { scrolled = it },
                    onCopy = {},
                    onMine = {},
                    onToggleStudy = {},
                    onClose = {},
                )
            }
        }
        compose.waitForIdle()

        compose.onRoot().performTouchInput {
            swipe(start = Offset(500f, 1500f), end = Offset(500f, 900f), durationMillis = 200)
        }
        compose.waitForIdle()
        assertEquals(true, scrolled)

        compose.onRoot().performTouchInput { click(Offset(500f, 1000f)) }
        compose.waitForIdle()
        assertNotNull(tapped)
    }
}
