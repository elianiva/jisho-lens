package com.elianiva.jisholens.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elianiva.jisholens.dictionary.RubySegment

@Composable
fun RubyText(
    segments: List<RubySegment>,
    modifier: Modifier = Modifier,
    baseSize: TextUnit = 24.sp,
    readingSize: TextUnit = 10.sp,
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
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Text(
                    text = segment.reading ?: " ",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = readingSize,
                        lineHeight = readingSize,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = segment.text,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = baseSize,
                        lineHeight = baseSize * 1.25f,
                    ),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}
