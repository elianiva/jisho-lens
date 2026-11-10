package com.elianiva.jisholens.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elianiva.jisholens.domain.PitchAccent
import com.elianiva.jisholens.domain.PitchShape
import com.elianiva.jisholens.domain.isHigh
import com.elianiva.jisholens.domain.label
import com.elianiva.jisholens.domain.morae
import com.elianiva.jisholens.domain.shape
import com.elianiva.jisholens.ui.theme.JapaneseLocale

/**
 * The pitch accent of a word as a contour over its morae: a high line for a high
 * mora, a low line for a low one, and a vertical step where the pitch drops.
 *
 * The morae double as the reading, so the contour replaces it rather than sitting
 * beside it. [fallbackReading] is used when the dictionary metadata has no reading
 * of its own.
 */
@Composable
fun PitchContour(
    accent: PitchAccent,
    fallbackReading: String,
    modifier: Modifier = Modifier,
) {
    val reading = accent.reading.ifBlank { fallbackReading }
    val moraList = remember(reading) { morae(reading) }
    if (moraList.isEmpty()) return
    val count = moraList.size
    val shape = accent.shape(count)
    val color = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val stroke = 2.dp
    val barHeight = 9.dp
    val slot = 22.dp

    Row(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "Pitch accent ${accent.label(count)}"
        },
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        moraList.forEachIndexed { index, mora ->
            val high = accent.isHigh(index + 1, count)
            val previousHigh = if (index == 0) null else accent.isHigh(index, count)
            Column(Modifier.width(slot), horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.fillMaxWidth().height(barHeight)) {
                    val line = stroke.toPx()
                    val highY = line / 2
                    val lowY = size.height - line / 2
                    val y = if (high) highY else lowY
                    drawLine(color, Offset(0f, y), Offset(size.width, y), line)
                    if (previousHigh != null && previousHigh != high) {
                        val from = if (previousHigh) highY else lowY
                        drawLine(color, Offset(0f, from), Offset(0f, y), line)
                    }
                }
                Text(
                    text = mora,
                    style = MaterialTheme.typography.labelLarge.copy(
                        localeList = JapaneseLocale,
                        fontWeight = if (high) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = if (high) textColor else textColor.copy(alpha = 0.72f),
                )
            }
        }
        if (shape == PitchShape.Odaka) {
            // Odaka drops on the particle after the word, so the step sits past the last mora.
            Canvas(Modifier.width(8.dp).height(barHeight)) {
                val line = stroke.toPx()
                drawLine(
                    color = color,
                    start = Offset(0f, line / 2),
                    end = Offset(0f, size.height - line / 2),
                    strokeWidth = line,
                )
            }
        }
    }
}
