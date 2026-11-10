package com.elianiva.jisholens.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.em
import com.elianiva.jisholens.R

/**
 * Google Sans Flex, the Material 3 Expressive typeface, subset to Latin with the weight and
 * roundness axes kept. Japanese falls through to the system CJK font.
 */
private fun flex(roundness: Float): FontFamily = FontFamily(
    listOf(300, 400, 500, 600, 700, 800).map { weight ->
        Font(
            resId = R.font.google_sans_flex,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(
                FontVariation.weight(weight),
                FontVariation.Setting("ROND", roundness),
            ),
        )
    },
)

/** Brand typeface for display, headline and title styles: rounder, for expression. */
val BrandFamily = flex(roundness = 60f)

/** Plain typeface for body and label styles: square, for reading. */
val PlainFamily = flex(roundness = 0f)

/**
 * Pins Han unification to Japanese. Without it a phone set to another language picks that
 * language's glyph variants for the same kanji.
 */
val JapaneseLocale = LocaleList("ja")

private val Base = Typography()

private fun TextStyle.brand(weight: FontWeight, tracking: Float = 0f) =
    copy(fontFamily = BrandFamily, fontWeight = weight, letterSpacing = tracking.em)

private fun TextStyle.plain(weight: FontWeight) = copy(fontFamily = PlainFamily, fontWeight = weight)

/**
 * Baseline styles plus the emphasized set from the expressive type scale. Emphasized styles
 * carry heavier weight and are used for headlines, selection and actions.
 */
val AppTypography = Typography(
    displayLarge = Base.displayLarge.brand(FontWeight.Normal, -0.045f),
    displayMedium = Base.displayMedium.brand(FontWeight.Normal, -0.045f),
    displaySmall = Base.displaySmall.brand(FontWeight.Normal, -0.045f),
    headlineLarge = Base.headlineLarge.brand(FontWeight.Normal, -0.03f),
    headlineMedium = Base.headlineMedium.brand(FontWeight.Normal, -0.03f),
    headlineSmall = Base.headlineSmall.brand(FontWeight.Normal, -0.03f),
    titleLarge = Base.titleLarge.brand(FontWeight.Normal, -0.02f),
    titleMedium = Base.titleMedium.brand(FontWeight.Medium),
    titleSmall = Base.titleSmall.brand(FontWeight.Medium),
    bodyLarge = Base.bodyLarge.plain(FontWeight.Normal),
    bodyMedium = Base.bodyMedium.plain(FontWeight.Normal),
    bodySmall = Base.bodySmall.plain(FontWeight.Normal),
    labelLarge = Base.labelLarge.plain(FontWeight.Medium),
    labelMedium = Base.labelMedium.plain(FontWeight.Medium),
    labelSmall = Base.labelSmall.plain(FontWeight.Medium),
    displayLargeEmphasized = Base.displayLargeEmphasized.brand(FontWeight.Bold, -0.045f),
    displayMediumEmphasized = Base.displayMediumEmphasized.brand(FontWeight.Bold, -0.045f),
    displaySmallEmphasized = Base.displaySmallEmphasized.brand(FontWeight.Bold, -0.045f),
    headlineLargeEmphasized = Base.headlineLargeEmphasized.brand(FontWeight.Bold, -0.03f),
    headlineMediumEmphasized = Base.headlineMediumEmphasized.brand(FontWeight.Bold, -0.03f),
    headlineSmallEmphasized = Base.headlineSmallEmphasized.brand(FontWeight.Bold, -0.03f),
    titleLargeEmphasized = Base.titleLargeEmphasized.brand(FontWeight.Bold, -0.02f),
    titleMediumEmphasized = Base.titleMediumEmphasized.brand(FontWeight.Bold),
    titleSmallEmphasized = Base.titleSmallEmphasized.brand(FontWeight.Bold),
    bodyLargeEmphasized = Base.bodyLargeEmphasized.plain(FontWeight.SemiBold),
    bodyMediumEmphasized = Base.bodyMediumEmphasized.plain(FontWeight.SemiBold),
    bodySmallEmphasized = Base.bodySmallEmphasized.plain(FontWeight.SemiBold),
    labelLargeEmphasized = Base.labelLargeEmphasized.plain(FontWeight.Bold),
    labelMediumEmphasized = Base.labelMediumEmphasized.plain(FontWeight.Bold),
    labelSmallEmphasized = Base.labelSmallEmphasized.plain(FontWeight.Bold),
)
