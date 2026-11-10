package com.elianiva.jisholens.data

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.elianiva.jisholens.domain.Entry
import com.elianiva.jisholens.domain.PitchAccent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowResolveInfo

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AnkiMiningTest {
    private val entry = Entry(
        termId = 1,
        dictionaryId = 1,
        dictionaryTitle = "Jitendex",
        expression = "食べる",
        reading = "たべる",
        ruby = emptyList(),
        tags = emptyList(),
        glossary = Json.parseToJsonElement("""["to eat", "to consume"]""") as JsonArray,
        frequency = 412.0,
        score = 10.0,
        deinflection = "食べた",
        pitch = listOf(PitchAccent("たべる", 2)),
    )

    @Test
    fun `the front carries the word and its reading`() {
        assertEquals("食べる（たべる）", buildAnkiNote(entry, sentence = null).front)
    }

    @Test
    fun `a kana headword is not repeated on the front`() {
        assertEquals("ラーメン", buildAnkiNote(entry.copy(expression = "ラーメン", reading = "ラーメン"), null).front)
    }

    @Test
    fun `the back carries gloss, sentence, pitch and source`() {
        val back = buildAnkiNote(entry, sentence = "ラーメンを食べた。").back
        val lines = back.split("\n\n")
        assertEquals("to eat; to consume", lines[0])
        assertEquals("ラーメンを食べた。", lines[1])
        assertEquals("nakadaka [2]", lines[2])
        assertEquals("Jitendex · Jisho Lens", lines[3])
    }

    @Test
    fun `a missing sentence leaves no empty block`() {
        val lines = buildAnkiNote(entry, sentence = "  ").back.split("\n\n")
        assertEquals(3, lines.size)
        assertEquals("nakadaka [2]", lines[1])
    }

    @Test
    fun `the intent targets AnkiDroid with the front as the subject`() {
        val intent = ankiDroidIntent(buildAnkiNote(entry, "ラーメンを食べた。"))
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("text/plain", intent.type)
        assertEquals("com.ichi2.anki", intent.`package`)
        assertEquals("食べる（たべる）", intent.getStringExtra(Intent.EXTRA_SUBJECT))
        assertTrue(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty().contains("ラーメンを食べた。"))
    }

    @Test
    fun `without AnkiDroid the note falls back to a share sheet`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertFalse(context.canMineToAnki())
        context.mineToAnki(buildAnkiNote(entry, null))
        val started = shadowOf(context as android.app.Application).nextStartedActivity
        assertNull(started.`package`)
        assertEquals(Intent.ACTION_CHOOSER, started.action)
    }

    @Test
    fun `with AnkiDroid installed the note goes straight there`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packageManager = context.packageManager
        val resolveInfo = ShadowResolveInfo.newResolveInfo("AnkiDroid", ANKI_PACKAGE, "NoteEditor")
        shadowOf(packageManager).addResolveInfoForIntent(
            Intent(Intent.ACTION_SEND).setType("text/plain").setPackage(ANKI_PACKAGE),
            resolveInfo,
        )
        assertTrue(context.canMineToAnki())
        context.mineToAnki(buildAnkiNote(entry, null))
        val started = shadowOf(context as android.app.Application).nextStartedActivity
        assertEquals(ANKI_PACKAGE, started.`package`)
        assertEquals(Intent.ACTION_SEND, started.action)
    }
}
