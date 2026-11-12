package com.elianiva.jisholens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import com.elianiva.jisholens.data.ImportState
import com.elianiva.jisholens.previews.Samples
import com.elianiva.jisholens.ui.components.WithoutDecorativeMotion
import com.elianiva.jisholens.ui.screens.DictionariesContent
import com.elianiva.jisholens.ui.screens.OnboardingContent
import com.elianiva.jisholens.ui.theme.JishoLensTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The catalogue in full, and the dictionary rows that say what each import is. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CatalogScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
    fun onboardingStarterSet() {
        compose.setContent {
            JishoLensTheme(dynamicColor = false) {
                WithoutDecorativeMotion {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                        OnboardingContent(onImport = {}, onSkip = {})
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/app/6-catalogue.png")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
    fun onboardingCatalogueTail() {
        compose.setContent {
            JishoLensTheme(dynamicColor = false) {
                WithoutDecorativeMotion {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                        OnboardingContent(onImport = {}, onSkip = {})
                    }
                }
            }
        }
        compose.onNodeWithText("Wikipedia (Japanese)").performScrollTo()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/app/8-catalogue-tail.png")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
    fun dictionaryKinds() {
        compose.setContent {
            JishoLensTheme(dynamicColor = false) {
                WithoutDecorativeMotion {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                        DictionariesContent(
                            dictionaries = Samples.dictionaries,
                            importState = ImportState.Idle,
                            storedBytes = 412L * 1024 * 1024,
                            snackbar = remember { SnackbarHostState() },
                            onBack = {},
                            onImport = {},
                            onDownloadSource = {},
                            onOpenSource = {},
                            onCancelImport = {},
                            onEnabled = { _, _ -> },
                            onMove = { _, _ -> },
                            onDelete = {},
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/app/7-dictionary-kinds.png")
    }
}
