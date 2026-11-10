package com.elianiva.jisholens

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The real app on first launch, with no dictionary: onboarding, then the three tabs. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class AppScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun waitFor(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun firstRun() {
        waitFor("Import dictionary")
        compose.onRoot().captureRoboImage("build/screenshots/app/1-first-run.png")

        compose.onNodeWithText("Skip for now").performClick()
        waitFor("Add a dictionary first")
        compose.onRoot().captureRoboImage("build/screenshots/app/2-lookup.png")

        compose.onNodeWithText("Scan").performClick()
        waitFor("Take a picture")
        compose.onRoot().captureRoboImage("build/screenshots/app/3-scan.png")

        compose.onNodeWithText("Settings").performClick()
        waitFor("Manage dictionaries")
        compose.onRoot().captureRoboImage("build/screenshots/app/4-settings.png")
    }
}

/** A tablet-width window gets the navigation rail instead of the bottom bar. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w840dp-h600dp-xhdpi")
class WideAppScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun rail() {
        // Earlier tests in this JVM may already have finished onboarding, so skip it only if shown.
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Skip for now").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("Add a dictionary first").fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodesWithText("Skip for now").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Skip for now").performClick()
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Add a dictionary first").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onRoot().captureRoboImage("build/screenshots/app/5-wide.png")
    }
}
