package com.elianiva.jisholens

import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The activity opens. The Compose rule owns the frame clock: the loading indicator in [MainActivity]
 * animates forever, and without it every frame posted another Choreographer callback until the
 * Robolectric looper ran out of heap instead of idling. The wait is bounded so that a regression
 * fails the test instead of hanging it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivitySmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun launches() {
        // Bounded on purpose: if Compose ever stops idling, this fails in ten seconds
        // instead of filling the heap with frame callbacks.
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(isRoot()).fetchSemanticsNodes().isNotEmpty()
        }
        assertNotNull(compose.activity)
    }
}
