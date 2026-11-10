package com.elianiva.jisholens.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.elianiva.jisholens.domain.EntryTag

/** Tags that mean "you will meet this word often"; they are filled, the rest are outlined. */
private val priorityTags = setOf("common", "P", "ichi1", "news1", "spec1", "gai1")

/**
 * A tag from the dictionary. Frequency and priority tags use the tertiary accent so they stand out
 * from grammar tags, which stay in the quieter secondary container. Tags that carry a note open it.
 */
@Composable
fun TagChip(tag: EntryTag, onClick: (EntryTag) -> Unit, modifier: Modifier = Modifier) {
    val priority = tag.name in priorityTags
    Surface(
        modifier = modifier.then(
            if (tag.notes != null) Modifier.clickable { onClick(tag) } else Modifier,
        ),
        shape = RoundedCornerShape(10.dp),
        color = if (priority) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor = if (priority) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        border = if (priority) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text = tag.name,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
