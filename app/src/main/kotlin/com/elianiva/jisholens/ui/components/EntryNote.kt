package com.elianiva.jisholens.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A saved note on the entry, or the quiet action that adds one. The note is local
 * to this device and keyed by the headword, so every dictionary shows the same one.
 */
@Composable
fun EntryNote(
    note: String,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
    expression: String = "",
) {
    var editing by remember { mutableStateOf(false) }
    if (note.isBlank()) {
        TextButton(onClick = { editing = true }, modifier = modifier.padding(top = 4.dp)) {
            Icon(Icons.AutoMirrored.Rounded.NoteAdd, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Add a note", modifier = Modifier.padding(start = 8.dp))
        }
    } else {
        Surface(
            modifier = modifier.padding(top = 12.dp).fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.StickyNote2,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(Modifier.weight(1f)) {
                    Text("Note", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(note, modifier = Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(onClick = { editing = true }) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Edit the note")
                }
            }
        }
    }
    if (editing) {
        NoteEditorDialog(
            note = note,
            expression = expression,
            onDismiss = { editing = false },
            onSave = { value ->
                onSave(value)
                editing = false
            },
        )
    }
}

/** The editor for one note. An empty value removes the note. */
@Composable
fun NoteEditorDialog(
    note: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    expression: String? = null,
) {
    var draft by remember(note) { mutableStateOf(note) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (expression.isNullOrBlank()) "Note" else "Note for $expression",
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
        },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Mnemonic or note") },
                minLines = 3,
                maxLines = 6,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
