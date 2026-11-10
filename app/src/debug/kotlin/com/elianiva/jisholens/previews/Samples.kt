package com.elianiva.jisholens.previews

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.elianiva.jisholens.data.TextLineBox
import com.elianiva.jisholens.dictionary.DictionaryRecord
import com.elianiva.jisholens.dictionary.Furigana
import com.elianiva.jisholens.domain.Entry
import com.elianiva.jisholens.domain.EntryTag
import com.elianiva.jisholens.domain.PitchAccent
import com.elianiva.jisholens.domain.ScanWord
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray

/** Sample content shared by the previews and the screenshot tests. */
internal object Samples {
    private fun glossary(json: String): JsonArray = Json.parseToJsonElement(json) as JsonArray

    val taberu = Entry(
        termId = 1,
        dictionaryId = 1,
        dictionaryTitle = "Jitendex",
        expression = "食べる",
        reading = "たべる",
        ruby = Furigana.align("食べる", "たべる"),
        tags = listOf(
            EntryTag("v1", "Ichidan verb"),
            EntryTag("vt", "Transitive verb"),
            EntryTag("common", "Common word"),
        ),
        glossary = glossary(
            """
            [{"type":"structured-content","content":[
              {"tag":"ul","content":[
                {"tag":"li","content":"to eat"},
                {"tag":"li","content":"to live on (e.g. a salary), to live off, to subsist on"},
                {"tag":"li","content":["to make a living ","(","see ",{"tag":"a","href":"?query=暮らす&wildcards=off","content":"暮らす"},")"]}
              ]}
            ]}]
            """.trimIndent(),
        ),
        frequency = 412.0,
        score = 10.0,
        deinflection = null,
        pitch = listOf(PitchAccent(reading = "たべる", position = 2)),
    )

    val taberareta = taberu.copy(
        termId = 2,
        deinflection = "食べさせられた",
        deinflectionReasons = listOf("causative", "passive", "past"),
    )

    val tabeta = taberu.copy(
        termId = 5,
        deinflection = "食べた",
        deinflectionReasons = listOf("past"),
    )

    val miru = Entry(
        termId = 3,
        dictionaryId = 2,
        dictionaryTitle = "JMdict",
        expression = "見る",
        reading = "みる",
        ruby = Furigana.align("見る", "みる"),
        tags = listOf(EntryTag("v1", "Ichidan verb"), EntryTag("vt", "Transitive verb")),
        glossary = glossary(
            """
            [{"type":"structured-content","content":[{"tag":"ol","content":[
              {"tag":"li","content":"to see, to look, to watch, to view"},
              {"tag":"li","content":"to examine, to look over, to check"},
              {"tag":"li","content":"to judge, to consider, to assess"}
            ]}]}]
            """.trimIndent(),
        ),
        frequency = 28.0,
        score = 9.0,
        deinflection = null,
        pitch = listOf(PitchAccent(reading = "みる", position = 1)),
    )

    val nihongo = Entry(
        termId = 4,
        dictionaryId = 1,
        dictionaryTitle = "Jitendex",
        expression = "日本語",
        reading = "にほんご",
        ruby = Furigana.align("日本語", "にほんご"),
        tags = listOf(EntryTag("n", "Noun"), EntryTag("common", "Common word")),
        glossary = glossary("""["Japanese (language)"]"""),
        frequency = 1204.0,
        score = 9.0,
        deinflection = null,
        pitch = listOf(PitchAccent(reading = "にほんご", position = 0)),
    )

    val dictionaries = listOf(
        DictionaryRecord(1, "Jitendex", "2026-10-01", 3, true, true, 0, null, 0L, true, true, mapOf("terms" to 295_132, "kanji" to 0)),
        DictionaryRecord(2, "JMdict (English)", "2026.10.29", 3, true, true, 1, null, 0L, true, true, mapOf("terms" to 214_880)),
        DictionaryRecord(3, "KANJIDIC", "2026-06", 3, false, false, 2, null, 0L, true, true, mapOf("kanji" to 13_108)),
    )

    /** The drawn page, built once and shared by the frozen-reader previews. */
    val screenPage: Pair<Bitmap, List<TextLineBox>> by lazy { scanPage() }

    /** A drawn page of Japanese text, so the scan screen can be shown without a camera. */
    fun scanPage(): Pair<Bitmap, List<TextLineBox>> {
        val bitmap = Bitmap.createBitmap(1000, 1400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(246, 240, 226))
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(40, 36, 40); textSize = 78f }
        val lines = listOf("昨日、友達と", "ラーメンを食べた。", "とても美味しかった。")
        val boxes = lines.mapIndexed { index, text ->
            val baseline = 420f + index * 170f
            canvas.drawText(text, 90f, baseline, ink)
            val width = ink.measureText(text).toInt()
            val rect = Rect(90, (baseline - 78).toInt(), 90 + width, (baseline + 18).toInt())
            TextLineBox(text, rect, emptyList())
        }
        return bitmap to boxes
    }

    val ramen = nihongo.copy(
        termId = 7,
        expression = "ラーメン",
        reading = "らーめん",
        ruby = Furigana.align("ラーメン", "らーめん"),
        tags = listOf(EntryTag("n", "Noun")),
        glossary = glossary("""["ramen (Chinese-style noodles)"]"""),
        frequency = 3410.0,
    )

    val oishii = taberu.copy(
        termId = 8,
        expression = "美味しい",
        reading = "おいしい",
        ruby = Furigana.align("美味しい", "おいしい"),
        tags = listOf(EntryTag("adj-i", "I-adjective")),
        glossary = glossary("""["delicious", "tasty", "sweet"]"""),
        frequency = 980.0,
    )

    val oishikatta = oishii.copy(
        termId = 9,
        deinflection = "美味しかった",
        deinflectionReasons = listOf("past"),
    )

    val scanWords = listOf(
        ScanWord("ラーメン", 0, 4, listOf(ramen)),
        ScanWord("を", 4, 5, emptyList()),
        ScanWord("食べた", 5, 8, listOf(tabeta)),
    )
}
