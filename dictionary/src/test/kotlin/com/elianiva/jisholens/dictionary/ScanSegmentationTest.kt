package com.elianiva.jisholens.dictionary

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Segmentation and lookup over real Japanese sentences.
 *
 * The dictionary fixture holds rows copied from Jitendex (readings, rules,
 * scores, and part-of-speech codes), and the TSV lists the word a reader would
 * tap in each sentence. The suite checks that every one of those words is a
 * chip, is the first answer of a tap, and is reachable by lookup.
 */
class ScanSegmentationTest {
    @Test
    fun `every fixture word is a chip and the first tap candidate`() {
        val lookup = fixture
        var checks = 0
        for ((text, targets) in targets().groupBy { it.text }) {
            val words = lookup.scan(text)
            var cursor = 0
            for (word in words) {
                assertTrue(word.start >= cursor, "chip ${word.surface} overlaps the one before it in $text")
                assertEquals(text.substring(word.start, word.end), word.surface)
                cursor = word.end
            }
            var from = 0
            for (target in targets) {
                val start = text.indexOf(target.surface, from)
                assertTrue(start >= 0, "fixture surface ${target.surface} is not in $text")
                from = start + target.surface.length
                if (target.segmented) {
                    val chip = words.any { word ->
                        word.start <= start && word.end > start &&
                            word.results.any { it.expression in target.headwords }
                    }
                    assertTrue(
                        chip,
                        "${target.surface} is missing from the chips of $text: " +
                            words.joinToString { "${it.surface}[${it.start},${it.end})" },
                    )
                }
                val candidates = lookup.scanAt(text, start)
                val first = candidates.firstOrNull()
                assertTrue(
                    first != null && first.results.any { it.expression in target.headwords },
                    "tap at ${target.surface} in $text leads with ${first?.let { it.surface to it.results.firstOrNull()?.expression }}, " +
                        "not ${target.headwords}",
                )
                assertTrue(
                    candidates.any { candidate -> candidate.results.any { it.expression in target.headwords } },
                    "tap at ${target.surface} in $text offers none of ${target.headwords}",
                )
                if (target.lookup) {
                    assertTrue(
                        lookup.lookup(target.surface).any { it.expression in target.headwords },
                        "lookup(${target.surface}) does not reach ${target.headwords}",
                    )
                }
                checks++
            }
        }
        assertEquals(111, checks)
    }

    @Test
    fun `a script change does not invent words across it`() {
        val words = fixture.scan("iPhoneのバッテリーがすぐ減る。").map { it.surface }
        assertTrue("バッテリー" in words, "expected バッテリー in $words")
        assertTrue(words.none { it in setOf("のバ", "テリ", "がす", "ぐ") }, "garbage chips $words")
    }

    @Test
    fun `a compound stays whole and its parts stay reachable`() {
        val text = "東京都渋谷区でイベントが開催される。"
        val words = fixture.scan(text).map { it.surface }
        assertTrue("東京都" in words, "expected 東京都 in $words")

        val candidates = fixture.scanAt(text, text.indexOf('京')).map { it.surface }
        assertTrue("東京都" in candidates, "expected 東京都 in $candidates")
        assertTrue("京都" in candidates, "expected 京都 in $candidates")
    }

    @Test
    fun `a conjugated word beats the shorter noun it contains`() {
        val text = "彼女は静かに窓を開けた。"
        val candidates = fixture.scanAt(text, text.indexOf('開'))
        assertEquals("開けた", candidates.first().surface)
        assertEquals("開ける", candidates.first().results.first().expression)
    }

    @Test
    fun `the same characters do not become the greeting`() {
        val text = "今日はいい天気ですね。"
        val first = fixture.scanAt(text, 0).first()
        assertEquals("今日", first.surface)
        assertEquals("今日", first.results.first().expression)
        assertTrue(
            fixture.scanAt(text, 0).any { it.surface == "今日は" },
            "the greeting is still offered as an alternative",
        )
    }

    @Test
    fun `a keigo phrase is reachable from its first word`() {
        val text = "お客様がお見えになりました。"
        val candidates = fixture.scanAt(text, text.indexOf("お見え"))
        assertTrue(
            candidates.any { candidate -> candidate.results.any { it.expression == "お見えになる" } },
            "expected お見えになる in ${candidates.map { it.surface }}",
        )
    }

    @Test
    fun `an unknown name does not split into single kanji`() {
        val words = fixture.scan("村上春樹の新刊を買った。").map { it.surface }
        assertTrue("新刊" in words, "expected 新刊 in $words")
        assertTrue("買った" in words, "expected 買った in $words")
        assertTrue(words.none { it in setOf("村", "上", "春", "樹") }, "single kanji chips $words")
    }

    @Test
    fun `a surface longer than a headword falls back to its parts`() {
        val results = fixture.lookup("渋谷区")
        assertTrue(results.any { it.expression == "渋谷" }, "expected 渋谷 in ${results.map { it.expression }}")
    }

    @Test
    fun `Jitendex part of speech codes come from the glossary`() {
        val kaisai = fixture.lookup("開催").first { it.expression == "開催" }
        assertTrue("vs" in kaisai.partsOfSpeech, "expected vs in ${kaisai.partsOfSpeech}")
        assertTrue(
            fixture.lookup("開催される").any { it.expression == "開催" },
            "開催される does not deinflect to 開催",
        )
        assertTrue(
            fixture.lookup("静かだった").any { it.expression == "静か" },
            "静かだった does not reach 静か",
        )
        assertTrue(
            fixture.search("開催される").any { it.expression == "開催" },
            "search does not deinflect 開催される",
        )
    }

    @Test
    fun `spaces and punctuation do not swallow the words next to them`() {
        val text = "明日 は 休み です。"
        val words = fixture.scan(text).map { it.surface }
        assertTrue("明日" in words, "expected 明日 in $words")
        assertTrue("休み" in words, "expected 休み in $words")
        assertEquals("休み", fixture.scanAt(text, text.indexOf("休み")).first().surface)
    }

    @Test
    fun `text without Japanese is not scanned`() {
        assertTrue(fixture.scan("Hello, world!").isEmpty())
        assertTrue(fixture.scanAt("Hello, world!", 0).isEmpty())
        assertTrue(fixture.scan("").isEmpty())
    }

    @Test
    fun `kana and kanji forms reach the same entry`() {
        val candidates = fixture.scanAt("リンゴを食べた。", 0)
        assertTrue(
            candidates.any { candidate -> candidate.results.any { it.expression == "林檎" } },
            "リンゴ did not reach 林檎: ${candidates.map { it.surface }}",
        )
    }

    @Test
    fun `an inflected word is not lost when no rule chain reaches it`() {
        val text = "印象的だった。"
        val words = fixture.scan(text)
        assertFalse(words.isEmpty(), "no chips for $text")
        assertTrue(
            words.any { it.results.any { result -> result.expression == "印象的" } },
            "expected 印象的 in ${words.map { it.surface }}",
        )
    }

    private data class Target(
        val text: String,
        val surface: String,
        val headwords: List<String>,
        val lookup: Boolean,
        /** False for a word that only exists inside a longer segmented phrase. */
        val segmented: Boolean,
    )

    private fun targets(): List<Target> = resource("/fixtures/scan-fixtures.tsv")
        .lineSequence()
        .filter { it.isNotBlank() }
        .map { line ->
            val parts = line.split('\t')
            Target(
                text = parts[0],
                surface = parts[1],
                headwords = parts[2].split('/'),
                lookup = parts.getOrElse(3) { "yes" } == "yes",
                segmented = parts.getOrElse(4) { "yes" } == "yes",
            )
        }
        .toList()

    private fun resource(path: String): String =
        javaClass.getResourceAsStream(path)!!.bufferedReader().use { it.readText() }

    private companion object {
        val fixture: DictionaryLookup by lazy {
            val database = Fixtures.database()
            Fixtures.archive(
                mapOf(
                    "index.json" to resourceBytes("/fixtures/index.json"),
                    "term_bank_1.json" to resourceBytes("/fixtures/term_bank_1.json"),
                ),
            ).use { archive -> YomitanImporter(database).import(archive) }
            DictionaryLookup(database)
        }

        fun resourceBytes(path: String): ByteArray =
            ScanSegmentationTest::class.java.getResourceAsStream(path)!!.readBytes()
    }
}
