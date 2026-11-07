package com.elianiva.jisholens.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.elianiva.jisholens.domain.EntryTag

@Composable
fun TagChip(tag: EntryTag, onClick: (EntryTag) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .then(if (tag.notes != null) Modifier.clickable { onClick(tag) } else Modifier),
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Text(
            text = tag.name,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
