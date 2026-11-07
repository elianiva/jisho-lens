package com.elianiva.jisholens.dictionary

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class YomitanImportTest {
    @Test
    fun `imports every bank`() {
        Fixtures.database().use { database ->
            Fixtures.archive(version3Files()).use { archive ->
                val summary = YomitanImporter(database).import(archive)
                assertEquals(
                    mapOf(
                        "tags" to 3,
                        "terms" to 5,
                        "termMeta" to 4,
                        "kanji" to 1,
                        "kanjiMeta" to 1,
                        "media" to 2,
                    ),
                    summary.counts,
                )
            }

            val dictionary = DictionaryStore(database).list().single()
            assertEquals("Test Dictionary", dictionary.title)
            assertEquals(3, dictionary.formatVersion)
            assertTrue(dictionary.sequenced)
            assertTrue(dictionary.enabled)
            assertEquals(5, dictionary.counts["terms"])

            val styles = database.query("SELECT styles FROM dictionaries").first().first()
            assertEquals("li { color: red; }", styles)

            val media = database.query("SELECT path, mediaType, width, height FROM media")
                .associateBy({ it[0] as String }, { Triple(it[1] as String, it[2], it[3]) })
            assertEquals("image/png", media.getValue("media/1.png").first)
            assertEquals(2L, media.getValue("media/1.png").second)
            assertEquals(3L, media.getValue("media/1.png").third)
            assertEquals("image/svg+xml", media.getValue("media/logo.svg").first)
            assertNull(media.getValue("media/logo.svg").second)
        }
    }

    @Test
    fun `stores term metadata as json`() {
        Fixtures.database().use { database ->
            Fixtures.archive(version3Files()).use { archive ->
                YomitanImporter(database).import(archive)
            }
            val rows = database.query("SELECT expression, mode, data FROM term_meta ORDER BY id")
            assertEquals(listOf("食べる", "食べ", "飲む", "食べる"), rows.map { it[0] })
            assertEquals("100", rows[0][2])
            assertEquals("""{"value":5,"displayValue":"five"}""", rows[1][2])
            assertEquals("\"three\"", rows[2][2])
        }
    }

    @Test
    fun `looks up exact words`() {
        Fixtures.database().use { database ->
            Fixtures.archive(version3Files()).use { archive -> YomitanImporter(database).import(archive) }
            val lookup = DictionaryLookup(database)

            val verb = lookup.lookup("食べる").single()
            assertEquals("たべる", verb.reading)
            assertEquals("v1 vt", verb.definitionTags)
            assertEquals("common", verb.termTags)
            assertEquals("v1", verb.rules)
            assertEquals(10.0, verb.score)
            assertEquals(100L, verb.sequence)
            assertEquals("to eat to consume", verb.glossaryText)

            assertEquals("テスト", lookup.lookup("テスト").single().expression)
            assertTrue(lookup.lookup("タベル").any { it.expression == "食べる" })
            assertTrue(lookup.lookup("知らない言葉").isEmpty())
        }
    }

    @Test
    fun `finishes an empty reading with the expression`() {
        Fixtures.database().use { database ->
            Fixtures.archive(version3Files()).use { archive -> YomitanImporter(database).import(archive) }
            val term = DictionaryLookup(database).lookup("テスト").single()
            assertEquals("テスト", term.reading)
            assertNull(term.sequence)
        }
    }

    @Test
    fun `searches japanese and english with fts`() {
        Fixtures.database().use { database ->
            Fixtures.archive(version3Files()).use { archive -> YomitanImporter(database).import(archive) }
            val lookup = DictionaryLookup(database)

            assertTrue(lookup.search("食べる").any { it.expression == "食べる" })
            assertTrue(lookup.search("たべ").any { it.expression == "食べる" })
            assertTrue(lookup.search("たべ").any { it.expression == "食べ" })
            assertTrue(lookup.search("eat").any { it.expression == "食べる" })
            assertTrue(lookup.search("Tシャツ").any { it.expression == "Ｔシャツ" })
            assertTrue(lookup.search("drink").any { it.expression == "飲む" })
        }
    }

    @Test
    fun `looks up kanji with meanings and stats`() {
        Fixtures.database().use { database ->
            Fixtures.archive(version3Files()).use { archive -> YomitanImporter(database).import(archive) }
            val lookup = DictionaryLookup(database)

            val kanji = lookup.kanji("食").single()
            assertEquals("ショク,ジキ", kanji.onyomi)
            assertEquals("く.う,た.べる", kanji.kunyomi)
            assertEquals("eat; food", kanji.meaningsText)
            assertEquals(9, (kanji.stats["strokes"] as JsonPrimitive).intOrNull)

            assertTrue(lookup.searchKanji("food").any { it.character == "食" })
        }
    }

    @Test
    fun `imports the old format one layout`() {
        Fixtures.database().use { database ->
            Fixtures.archive(legacyFiles()).use { archive ->
                val summary = YomitanImporter(database).import(archive)
                assertEquals(1, summary.counts["terms"])
                assertEquals(1, summary.counts["kanji"])
            }
            val lookup = DictionaryLookup(database)
            val term = lookup.lookup("走る").single()
            assertEquals("to run; to dash", term.glossaryText)
            assertEquals("走", lookup.kanji("走").single().character)
            assertEquals("to run; extra meaning", lookup.kanji("走").single().meaningsText)
        }
    }

    @Test
    fun `falls back to tag metadata from the index`() {
        Fixtures.database().use { database ->
            Fixtures.archive(tagMetaFiles()).use { archive -> YomitanImporter(database).import(archive) }
            val tags = database.query("SELECT name, category, sortOrder, notes FROM tags ORDER BY name")
            assertEquals(2, tags.size)
            assertEquals(listOf("n", "v5"), tags.map { it[0] })
            assertEquals("noun", tags[0][3])
        }
    }

    @Test
    fun `re-importing a dictionary replaces it`() {
        Fixtures.database().use { database ->
            repeat(2) {
                Fixtures.archive(version3Files()).use { archive -> YomitanImporter(database).import(archive) }
            }
            assertEquals(1, DictionaryStore(database).list().size)
            assertEquals(5, count(database, "terms"))
            assertEquals(5, count(database, "terms_fts"))
            assertEquals(1, count(database, "kanji"))
            assertEquals(1, count(database, "kanji_fts"))
        }
    }

    @Test
    fun `deleting a dictionary removes its rows`() {
        Fixtures.database().use { database ->
            Fixtures.archive(version3Files()).use { archive -> YomitanImporter(database).import(archive) }
            val store = DictionaryStore(database)
            val dictionary = store.list().single()

            store.delete(dictionary.id)

            assertTrue(store.list().isEmpty())
            for (table in listOf("terms", "terms_fts", "glossary_blocks", "term_meta", "kanji", "kanji_fts", "kanji_meta", "tags", "media")) {
                assertEquals(0, count(database, table), "$table should be empty")
            }
        }
    }

    @Test
    fun `a disabled dictionary is not searched`() {
        Fixtures.database().use { database ->
            Fixtures.archive(version3Files()).use { archive -> YomitanImporter(database).import(archive) }
            val store = DictionaryStore(database)
            val dictionary = store.list().single()

            store.setEnabled(dictionary.id, false)
            assertTrue(DictionaryLookup(database).lookup("食べる").isEmpty())
            assertTrue(DictionaryLookup(database).lookup("食べさせられた").isEmpty())
            assertTrue(DictionaryLookup(database).search("eat").isEmpty())

            store.setEnabled(dictionary.id, true)
            assertEquals(1, DictionaryLookup(database).lookup("食べる").size)
        }
    }

    @Test
    fun `orders banks by their numeric suffix`() {
        Fixtures.archive(
            mapOf(
                "index.json" to """{"title":"Ordered","format":3}""".toByteArray(),
                "term_bank_10.json" to "[]".toByteArray(),
                "term_bank_2.json" to "[]".toByteArray(),
                "term_bank_1.json" to "[]".toByteArray(),
            ),
        ).use { archive ->
            assertEquals(
                listOf("term_bank_1.json", "term_bank_2.json", "term_bank_10.json"),
                archive.bankNames("term_bank_"),
            )
        }
    }

    @Test
    fun `reports a missing index`() {
        Fixtures.archive(mapOf("term_bank_1.json" to "[]".toByteArray())).use { archive ->
            val error = runCatching { archive.readIndex() }.exceptionOrNull()
            assertNotNull(error)
            assertTrue(error is YomitanFormatException)
        }
    }

    @Test
    fun `exposes tags term metadata media and styles`() {
        Fixtures.database().use { database ->
            Fixtures.archive(version3Files()).use { archive -> YomitanImporter(database).import(archive) }
            val store = DictionaryStore(database)
            val dictionary = store.list().single()

            val tags = store.tags(dictionary.id).associateBy { it.name }
            assertEquals(3, tags.size)
            assertEquals("noun", tags.getValue("n").notes)
            assertEquals("partOfSpeech", tags.getValue("v1").category)

            val metadata = store.termMeta(listOf("食べる", "飲む")).associateBy { "${it.expression}:${it.mode}" }
            assertEquals("100", metadata.getValue("食べる:freq").data)
            assertEquals("\"three\"", metadata.getValue("飲む:freq").data)

            val media = store.media(dictionary.id, "media/1.png")
            assertNotNull(media)
            assertEquals("image/png", media.mediaType)
            assertEquals(2, media.width)
            assertEquals(3, media.height)
            assertEquals(24, media.data.size)
            assertEquals("li { color: red; }", store.styles(dictionary.id))
        }
    }

    @Test
    fun `cancels an import and rolls back`() {
        Fixtures.database().use { database ->
            var progressCalls = 0
            val importer = YomitanImporter(
                database = database,
                onProgress = { progressCalls++ },
                shouldContinue = { progressCalls < 2 },
            )
            Fixtures.archive(version3Files()).use { archive ->
                val error = runCatching { importer.import(archive) }.exceptionOrNull()
                assertTrue(error is ImportCancelledException)
            }
            assertTrue(DictionaryStore(database).list().isEmpty())
            val terms = database.query("SELECT COUNT(*) FROM terms").first().first() as Number
            assertEquals(0, terms.toInt())
        }
    }

    @Test
    fun `scans a line with exact and deinflected words`() {
        Fixtures.database().use { database ->
            Fixtures.archive(version3Files()).use { archive -> YomitanImporter(database).import(archive) }
            val lookup = DictionaryLookup(database)

            val matches = lookup.scan("食べる人と飲む")
            assertEquals(listOf("食べる", "飲む"), matches.map { it.surface })
            assertEquals("食べる", matches.first().results.first().expression)

            val conjugated = lookup.scan("食べさせられた")
            assertEquals(listOf("食べさせられた"), conjugated.map { it.surface })
            assertEquals("食べる", conjugated.first().results.first().expression)
        }
    }

    private fun count(database: SqliteDatabase, table: String): Int =
        (database.query("SELECT COUNT(*) FROM $table").first().first() as Number).toInt()

    private fun version3Files(): Map<String, ByteArray> = mapOf(
        "index.json" to """
            {"title":"Test Dictionary","format":3,"revision":"2026.01.01","sequenced":true,
             "prefixWildcardsSupported":true,"author":"Tester","sourceLanguage":"ja","targetLanguage":"en"}
        """.trimIndent().toByteArray(),
        "term_bank_1.json" to """
            [
              ["食べる","たべる","v1 vt","v1",10,[{"type":"structured-content","content":{"tag":"ul","content":[
                {"tag":"li","content":["to eat","to consume"]}]}}],100,"common"],
              ["食べ","たべ","n","",5,["eating","to live on"],100,""],
              ["Ｔシャツ","ティーシャツ","n","",3,["T-shirt"],200,""],
              ["テスト","","n","",1,["test"],null,""]
            ]
        """.trimIndent().toByteArray(),
        "term_bank_2.json" to """[["飲む","のむ","v5m","v5m",2,["to drink"],300,""]]""".toByteArray(),
        "kanji_bank_1.json" to """
            [["食","ショク,ジキ","く.う,た.べる","",["eat","food"],{"strokes":9,"frequency":1}]]
        """.trimIndent().toByteArray(),
        "term_meta_bank_1.json" to """
            [
              ["食べる","freq",100],
              ["食べ","freq",{"value":5,"displayValue":"five"}],
              ["飲む","freq","three"],
              ["食べる","pitch",{"reading":"たべる","pitches":[{"position":1,"tags":[]}]}]
            ]
        """.trimIndent().toByteArray(),
        "kanji_meta_bank_1.json" to """[["食","freq",1]]""".toByteArray(),
        "tag_bank_1.json" to """
            [["n","partOfSpeech",1,"noun",0],["v1","partOfSpeech",2,"",0],["common","default",0,"",0]]
        """.trimIndent().toByteArray(),
        "styles.css" to "li { color: red; }".toByteArray(),
        "media/1.png" to Fixtures.png(2, 3),
        "media/logo.svg" to "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".toByteArray(),
    )

    private fun legacyFiles(): Map<String, ByteArray> = mapOf(
        "index.json" to """{"title":"Legacy Dictionary","format":1}""".toByteArray(),
        "term_bank_1.json" to """[["走る","はしる","v5r","v5r",1,"to run","to dash"]]""".toByteArray(),
        "kanji_bank_1.json" to """[["走","ソウ","はし.る","","to run","extra meaning"]]""".toByteArray(),
    )

    private fun tagMetaFiles(): Map<String, ByteArray> = mapOf(
        "index.json" to """
            {"title":"Meta Tags","format":3,"tagMeta":{
                "n":{"category":"partOfSpeech","order":1,"notes":"noun","score":0.5},
                "v5":{"category":"partOfSpeech"}
            }}
        """.trimIndent().toByteArray(),
        "term_bank_1.json" to """[["走る","はしる","v5","v5",1,["to run"],1,""]]""".toByteArray(),
    )
}
