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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import com.elianiva.jisholens.dictionary.RubySegment
import com.elianiva.jisholens.domain.isKanji
import com.elianiva.jisholens.ui.theme.JapaneseLocale

/**
 * Text with furigana set above each segment. Every segment reserves a reading line, so bases with
 * and without a reading share one baseline. A kanji base is a link when [onKanji] is set: the
 * character opens its own detail screen.
 */
@Composable
fun RubyText(
    segments: List<RubySegment>,
    modifier: Modifier = Modifier,
    baseStyle: TextStyle = MaterialTheme.typography.headlineMedium,
    readingStyle: TextStyle = MaterialTheme.typography.labelMedium,
    readingColor: Color = MaterialTheme.colorScheme.primary,
    onKanji: ((Char) -> Unit)? = null,
) {
    if (segments.isEmpty()) return
    val description = segments.joinToString("") { it.text } +
        segments.mapNotNull { it.reading }.joinToString("", prefix = "、")
    FlowRow(
        // Links need their own semantics to stay reachable; without them one spoken word is enough.
        modifier = if (onKanji == null) {
            modifier.clearAndSetSemantics { contentDescription = description }
        } else {
            modifier
        },
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
                val base = if (onKanji == null) {
                    AnnotatedString(segment.text)
                } else {
                    annotatedKanji(segment.text, onKanji)
                }
                Text(
                    text = base,
                    style = baseStyle.copy(localeList = JapaneseLocale),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/** One clickable link per kanji, with a thin underline as the only mark of the tap. */
private fun annotatedKanji(text: String, onKanji: (Char) -> Unit): AnnotatedString = buildAnnotatedString {
    text.forEach { character ->
        if (character.isKanji()) {
            withLink(
                LinkAnnotation.Clickable(
                    tag = "kanji:$character",
                    styles = TextLinkStyles(style = SpanStyle(textDecoration = TextDecoration.Underline)),
                    linkInteractionListener = { onKanji(character) },
                ),
            ) {
                append(character)
            }
        } else {
            append(character)
        }
    }
}
