package com.elianiva.jisholens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.elianiva.jisholens.data.AppContainer
import com.elianiva.jisholens.data.DictionaryDatabase
import com.elianiva.jisholens.data.DictionaryRepository
import com.elianiva.jisholens.dictionary.Furigana
import com.elianiva.jisholens.domain.Entry
import com.elianiva.jisholens.domain.EntryTag
import com.elianiva.jisholens.ui.components.EntryCard
import com.elianiva.jisholens.ui.screens.HomeScreen
import com.elianiva.jisholens.ui.screens.OnboardingScreen
import com.elianiva.jisholens.ui.screens.SearchScreen
import com.elianiva.jisholens.ui.screens.SettingsScreen
import com.elianiva.jisholens.ui.theme.JishoLensTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val repository = DictionaryRepository(DictionaryDatabase(context))

    @Test
    fun entryCardLight() {
        compose.setContent {
            JishoLensTheme(darkTheme = false, dynamicColor = false) {
                Screen { EntryCard(entry(), repository, onQuery = {}) }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/entry-card-light.png")
    }

    @Test
    fun entryCardDark() {
        compose.setContent {
            JishoLensTheme(darkTheme = true, dynamicColor = false) {
                Screen { EntryCard(entry(), repository, onQuery = {}) }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/entry-card-dark.png")
    }

    @Test
    fun onboarding() {
        val container = AppContainer(context)
        compose.setContent {
            JishoLensTheme(darkTheme = false, dynamicColor = false) {
                OnboardingScreen(container = container, onImport = {}, onDone = {})
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/onboarding.png")
    }

    @Test
    fun home() {
        val container = AppContainer(context)
        compose.setContent {
            JishoLensTheme(darkTheme = false, dynamicColor = false) {
                HomeScreen(container = container, onScan = {}, onOpenDictionaries = {})
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/home.png")
    }

    @Test
    fun search() {
        val container = AppContainer(context)
        compose.setContent {
            JishoLensTheme(darkTheme = false, dynamicColor = false) {
                SearchScreen(container = container, onOpenDictionaries = {})
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/search.png")
    }

    @Test
    fun settings() {
        val container = AppContainer(context)
        compose.setContent {
            JishoLensTheme(darkTheme = false, dynamicColor = false) {
                SettingsScreen(container = container, onOpenDictionaries = {})
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/settings.png")
    }

    @Composable
    private fun Screen(content: @Composable () -> Unit) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
        ) {
            content()
        }
    }

    private fun entry(): Entry = Entry(
        termId = 1,
        dictionaryId = 1,
        dictionaryTitle = "Jitendex",
        expression = "食べる",
        reading = "たべる",
        ruby = Furigana.align("食べる", "たべる"),
        tags = listOf(
            EntryTag("v1", "Ichidan verb"),
            EntryTag("vt", "Transitive verb"),
            EntryTag("common", "Common word"),
        ),
        glossary = Json.parseToJsonElement(
            """
            [
              {"type":"structured-content","content":{"tag":"ul","content":[
                {"tag":"li","content":["to eat","to consume"]},
                {"tag":"li","content":["to live on","to make a living"]}
              ]}},
              "food"
            ]
            """.trimIndent(),
        ) as JsonArray,
        frequency = 100.0,
        score = 10.0,
        deinflection = null,
    )
}
