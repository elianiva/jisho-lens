package com.elianiva.jisholens.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.elianiva.jisholens.R

val Inter = FontFamily(Font(R.font.inter_regular))

private val Base = Typography()

val AppTypography = Base.copy(
    displayLarge = Base.displayLarge.copy(fontFamily = Inter),
    displayMedium = Base.displayMedium.copy(fontFamily = Inter),
    displaySmall = Base.displaySmall.copy(fontFamily = Inter),
    headlineLarge = Base.headlineLarge.copy(fontFamily = Inter),
    headlineMedium = Base.headlineMedium.copy(fontFamily = Inter),
    headlineSmall = Base.headlineSmall.copy(fontFamily = Inter),
    titleLarge = Base.titleLarge.copy(fontFamily = Inter),
    titleMedium = Base.titleMedium.copy(fontFamily = Inter, fontSize = 14.sp),
    titleSmall = Base.titleSmall.copy(fontFamily = Inter),
    bodyLarge = Base.bodyLarge.copy(fontFamily = Inter, fontSize = 16.sp),
    bodyMedium = Base.bodyMedium.copy(fontFamily = Inter, fontSize = 14.sp, lineHeight = 24.5.sp),
    bodySmall = Base.bodySmall.copy(fontFamily = Inter, lineHeight = 20.sp),
    labelLarge = Base.labelLarge.copy(fontFamily = Inter, fontSize = 14.sp),
    labelMedium = Base.labelMedium.copy(fontFamily = Inter, fontSize = 12.sp),
    labelSmall = Base.labelSmall.copy(fontFamily = Inter),
)
