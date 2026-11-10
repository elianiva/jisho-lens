package com.elianiva.jisholens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import sergio.sastre.composable.preview.scanner.android.AndroidComposablePreviewScanner
import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo
import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview

/**
 * Renders every `@Preview` in the debug source set on the JVM and writes it to
 * `app/build/screenshots/previews`. Adding a preview adds a screenshot.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PreviewScreenshotTest(private val preview: ComposablePreview<AndroidPreviewInfo>) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun capture() {
        val info = preview.previewInfo
        RuntimeEnvironment.setQualifiers("w${info.widthDp}dp-h${info.heightDp}dp-xxhdpi")
        compose.setContent { preview() }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/previews/${preview.methodName}.png")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun previews(): List<ComposablePreview<AndroidPreviewInfo>> =
            AndroidComposablePreviewScanner()
                .scanPackageTrees("com.elianiva.jisholens.previews")
                .getPreviews()
    }
}
