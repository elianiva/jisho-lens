package com.elianiva.jisholens.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** The large expressive button: tall, with a leading icon, and its corners give way when pressed. */
@Composable
fun LargeActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tonal: Boolean = false,
) {
    val content: @Composable RowScope.() -> Unit = {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.LargeIconSize))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.titleMediumEmphasized)
    }
    val sized = modifier.heightIn(min = ButtonDefaults.LargeContainerHeight)
    if (tonal) {
        FilledTonalButton(
            onClick = onClick,
            modifier = sized,
            shapes = ButtonDefaults.shapes(),
            contentPadding = ButtonDefaults.LargeContentPadding,
            content = content,
        )
    } else {
        Button(
            onClick = onClick,
            modifier = sized,
            shapes = ButtonDefaults.shapes(),
            contentPadding = ButtonDefaults.LargeContentPadding,
            content = content,
        )
    }
}
