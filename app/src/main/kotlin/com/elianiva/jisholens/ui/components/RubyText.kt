package com.elianiva.jisholens.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.elianiva.jisholens.dictionary.RubySegment
import com.elianiva.jisholens.ui.theme.JapaneseLocale

/**
 * Text with furigana set above each segment. Every segment reserves a reading line, so bases with
 * and without a reading share one baseline.
 */
@Composable
fun RubyText(
    segments: List<RubySegment>,
    modifier: Modifier = Modifier,
    baseStyle: TextStyle = MaterialTheme.typography.headlineMedium,
    readingStyle: TextStyle = MaterialTheme.typography.labelMedium,
    readingColor: Color = MaterialTheme.colorScheme.primary,
) {
    if (segments.isEmpty()) return
    val description = segments.joinToString("") { it.text } +
        segments.mapNotNull { it.reading }.joinToString("", prefix = "、")
    FlowRow(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.Start,
        verticalArrangement = Arrangement.Bottom,
    ) {
        for (segment in segments) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = segment.reading ?: " ",
                    style = readingStyle.copy(
                        localeList = JapaneseLocale,
                        lineHeight = readingStyle.fontSize,
                    ),
                    color = readingColor,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = segment.text,
                    style = baseStyle.copy(localeList = JapaneseLocale),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}
