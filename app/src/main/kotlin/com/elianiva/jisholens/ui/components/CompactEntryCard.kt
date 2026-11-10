package com.elianiva.jisholens.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.elianiva.jisholens.dictionary.Glossary
import com.elianiva.jisholens.domain.Entry
import com.elianiva.jisholens.ui.theme.JapaneseLocale

/**
 * The card that sits over a frozen screen and under a selection.
 *
 * It leads with the word, then explains a conjugated surface, then gives the first
 * few senses in one paragraph. [onExpand] opens the full entry, which is where every
 * gloss, tag, image, and link lives.
 */
@Composable
fun CompactEntryCard(
    entry: Entry,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
    ) {
        Column(Modifier.padding(start = 20.dp, top = 16.dp, end = 12.dp, bottom = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    RubyText(
                        segments = entry.ruby,
                        baseStyle = MaterialTheme.typography.headlineMediumEmphasized,
                        readingStyle = MaterialTheme.typography.labelMedium,
                    )
                    if (entry.ruby.isEmpty()) {
                        Text(
                            text = entry.expression,
                            style = MaterialTheme.typography.headlineMediumEmphasized.copy(localeList = JapaneseLocale),
                        )
                    }
                    val pitch = entry.pitch.firstOrNull()
                    if (pitch != null) {
                        PitchContour(
                            accent = pitch,
                            fallbackReading = entry.reading,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    } else if (entry.reading.isNotBlank() && entry.reading != entry.expression &&
                        entry.ruby.none { it.reading != null }
                    ) {
                        Text(
                            text = entry.reading,
                            style = MaterialTheme.typography.titleSmall.copy(localeList = JapaneseLocale),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (entry.frequency != null) FrequencyPill(entry.frequency)
                IconButton(onClick = onExpand) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = "Open the full entry",
                    )
                }
            }

            if (entry.deinflection != null) {
                Text(
                    text = deinflectionLine(entry),
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.labelLarge.copy(localeList = JapaneseLocale),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val gloss = compactGloss(Glossary.text(entry.glossary))
            if (gloss.isNotBlank()) {
                Text(
                    text = gloss,
                    modifier = Modifier.padding(top = 10.dp, end = 8.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    if (entry.dictionaryTitle.isNotBlank()) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = entry.dictionaryTitle,
                            modifier = Modifier.padding(start = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                actions()
            }
        }
    }
}

/** The first two senses as one paragraph; the full entry has the rest. */
internal fun compactGloss(text: String): String =
    text.split(';').take(2).joinToString(";").trim()

/** `食べさせられた → 食べる`, then the rules that connect them. */
internal fun deinflectionLine(entry: Entry): String {
    val surface = entry.deinflection ?: return ""
    val rules = entry.deinflectionReasons.joinToString(" + ")
    val arrow = "$surface → ${entry.expression}"
    return if (rules.isEmpty()) arrow else "$arrow\n$rules"
}
