package com.elianiva.jisholens.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.elianiva.jisholens.dictionary.Glossary
import com.elianiva.jisholens.domain.Entry
import com.elianiva.jisholens.domain.PitchAccent
import com.elianiva.jisholens.domain.label
import com.elianiva.jisholens.domain.morae

/** The two fields of a basic Anki note, ready for AnkiDroid. */
data class AnkiNote(val front: String, val back: String)

/**
 * Builds the note for one mined word: the word and its reading on the front, the
 * gloss, the sentence it came from, the pitch accent, and the dictionary on the back.
 *
 * The sentence is the part that makes a card worth reviewing, so it goes in whole;
 * a card without it still works.
 */
fun buildAnkiNote(entry: Entry, sentence: String?, pitch: PitchAccent? = entry.pitch.firstOrNull()): AnkiNote {
    val front = if (entry.reading.isNotBlank() && entry.reading != entry.expression) {
        "${entry.expression}（${entry.reading}）"
    } else {
        entry.expression
    }
    val back = buildString {
        val gloss = Glossary.text(entry.glossary).trim()
        if (gloss.isNotEmpty()) append(gloss)
        val line = sentence?.trim().orEmpty()
        if (line.isNotEmpty()) {
            if (isNotEmpty()) append("\n\n")
            append(line)
        }
        if (pitch != null) {
            val count = morae(pitch.reading.ifBlank { entry.reading }).size
            if (isNotEmpty()) append("\n\n")
            append(pitch.label(count))
        }
        if (isNotEmpty()) append("\n\n")
        append(entry.dictionaryTitle.ifBlank { "Jisho Lens" })
        append(" · Jisho Lens")
    }
    return AnkiNote(front = front, back = back)
}

/**
 * Opens AnkiDroid with the note filled in.
 *
 * AnkiDroid's Instant-Add library is LGPL-3.0, which this MIT app must not absorb,
 * so the app uses the documented `ACTION_SEND` contract instead: the front goes in
 * the subject, the back in the text, and the user confirms in AnkiDroid's editor.
 * When AnkiDroid is not installed the same text goes to the system share sheet.
 */
fun Context.mineToAnki(note: AnkiNote) {
    val direct = ankiDroidIntent(note)
    val resolved = packageManager.resolveActivity(direct, PackageManager.MATCH_DEFAULT_ONLY) != null
    val intent = if (resolved) direct else Intent.createChooser(shareIntent(note), "Send to AnkiDroid")
    // The reader calls this from an activity; anything else needs its own task.
    if (this !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}

/** True when AnkiDroid can take a note, so the action can explain itself in the UI. */
fun Context.canMineToAnki(): Boolean =
    packageManager.resolveActivity(ankiDroidIntent(AnkiNote("", "")), PackageManager.MATCH_DEFAULT_ONLY) != null

internal fun ankiDroidIntent(note: AnkiNote): Intent = Intent(Intent.ACTION_SEND).apply {
    type = "text/plain"
    setPackage(ANKI_PACKAGE)
    putExtra(Intent.EXTRA_SUBJECT, note.front)
    putExtra(Intent.EXTRA_TEXT, note.back)
}

internal fun shareIntent(note: AnkiNote): Intent = Intent(Intent.ACTION_SEND).apply {
    type = "text/plain"
    putExtra(Intent.EXTRA_SUBJECT, note.front)
    putExtra(Intent.EXTRA_TEXT, note.back)
}

/** Package name of AnkiDroid, the only deck that this action talks to. */
const val ANKI_PACKAGE = "com.ichi2.anki"
