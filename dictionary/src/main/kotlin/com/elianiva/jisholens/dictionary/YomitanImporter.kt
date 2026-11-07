package com.elianiva.jisholens.dictionary

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class ImportSummary(
    val dictionaryId: Long,
    val title: String,
    val counts: Map<String, Int>,
    val durationMillis: Long,
)

/**
 * Imports a Yomitan archive into the SQLite schema.
 *
 * The whole import runs in one transaction, so a failure leaves no partial
 * dictionary behind. Importing a dictionary whose title already exists replaces
 * it, keeping its priority, alias, and enabled state.
 *
 * Rows are read as column spans over the bank text and appended to columnar
 * batches, so a 500,000-row dictionary does not allocate an object tree per
 * entry.
 */
class YomitanImporter(
    private val database: SqliteDatabase,
    private val onProgress: (String) -> Unit = {},
    private val batchSize: Int = 2_000,
) {
    private val store = DictionaryStore(database)

    fun import(archive: YomitanArchive, alias: String? = null): ImportSummary {
        val startedAt = System.nanoTime()
        val index = archive.readIndex()
        val previous = store.findByTitle(index.title)
        onProgress("Importing ${index.title} (format ${index.formatVersion})")

        val counts = database.transaction {
            if (previous != null) {
                store.delete(previous.id)
            }
            val dictionaryId = insertDictionary(
                index = index,
                alias = alias ?: previous?.alias,
                priority = previous?.priority,
                enabled = previous?.enabled ?: true,
            )
            // Indexes are rebuilt once at the end: maintaining six b-trees for
            // every inserted row is the slowest part of a bulk load.
            DictionarySchema.dropIndexes(database)

            val imported = LinkedHashMap<String, Int>()
            imported["tags"] = importTags(archive, dictionaryId, index)
            imported["terms"] = importTerms(archive, dictionaryId, index)
            imported["termMeta"] = importTermMeta(archive, dictionaryId)
            imported["kanji"] = importKanji(archive, dictionaryId, index)
            imported["kanjiMeta"] = importKanjiMeta(archive, dictionaryId)
            imported["media"] = importMedia(archive, dictionaryId)

            database.execute(
                "UPDATE dictionaries SET countsJson = ? WHERE id = ?",
                listOf(countsJson(imported), dictionaryId),
            )
            DictionarySchema.createIndexes(database)
            if (needsReverseIndexes()) {
                DictionarySchema.createReverseIndexes(database)
            }
            dictionaryId to imported
        }

        val durationMillis = (System.nanoTime() - startedAt) / 1_000_000
        return ImportSummary(counts.first, index.title, counts.second, durationMillis)
    }

    private fun insertDictionary(
        index: YomitanIndex,
        alias: String?,
        priority: Int?,
        enabled: Boolean,
    ): Long {
        val nextPriority = priority
            ?: ((database.query("SELECT COALESCE(MAX(priority), -1) + 1 FROM dictionaries").first().first() as Number).toInt())
        database.execute(
            """
            INSERT INTO dictionaries (
                title, revision, formatVersion, sequenced, enabled, priority, alias, importDate,
                importSuccess, prefixWildcardsSupported, styles, frequencyMode, sourceLanguage,
                targetLanguage, author, url, description, attribution, isUpdatable, indexUrl,
                downloadUrl, countsJson
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            listOf(
                index.title,
                index.revision,
                index.formatVersion,
                index.sequenced.asInt(),
                enabled.asInt(),
                nextPriority,
                alias,
                System.currentTimeMillis(),
                1,
                index.prefixWildcardsSupported.asInt(),
                index.styles,
                index.frequencyMode,
                index.sourceLanguage,
                index.targetLanguage,
                index.author,
                index.url,
                index.description,
                index.attribution,
                index.isUpdatable?.asInt(),
                index.indexUrl,
                index.downloadUrl,
                null,
            ),
        )
        return (database.query("SELECT last_insert_rowid()").first().first() as Number).toLong()
    }

    private fun importTags(archive: YomitanArchive, dictionaryId: Long, index: YomitanIndex): Int {
        val tags = ArrayList<YomitanTag>()
        archive.bankNames("tag_bank_").forEach { bank ->
            archive.forEachBankRow(bank) { row ->
                YomitanParsing.tag(row)?.let { tags.add(it) }
            }
        }
        if (tags.isEmpty()) {
            tags.addAll(index.tagMeta)
        }
        if (tags.isEmpty()) return 0

        var id = nextId("tags")
        val batch = TagBatch(batchSize, dictionaryId)
        tags.forEach { tag ->
            batch.add(id++, tag)
            if (batch.size == batchSize) flush(batch)
        }
        flush(batch)
        onProgress("Imported ${tags.size} tags")
        return tags.size
    }

    private fun importTerms(archive: YomitanArchive, dictionaryId: Long, index: YomitanIndex): Int {
        val batch = TermBatch(batchSize, dictionaryId)
        val fts = TermFtsBatch(batchSize)
        val v3 = index.formatVersion != 1
        var nextId = nextId("terms")
        var total = 0

        GlossaryBlockWriter(database, dictionaryId).use { glossaries ->
            for (bank in archive.bankNames("term_bank_")) {
                var bankCount = 0
                archive.forEachBankRow(bank) { row ->
                    val expression = row.string(0)
                    if (!expression.isNullOrEmpty()) {
                        val reading = row.string(1)?.ifEmpty { expression } ?: expression
                        val glossaryJson = if (!v3) {
                            arrayFrom(row, 5)
                        } else {
                            if (row.has(5)) row.raw(5) else "[]"
                        }
                        val glossaryText = if (v3 && row.isArray(5)) {
                            Glossary.text(row.source, row.start(5), row.end(5))
                        } else {
                            Glossary.text(glossaryJson)
                        }
                        if (v3 && row.isArray(5)) {
                            glossaries.add(row.source, row.start(5), row.end(5))
                        } else {
                            glossaries.add(glossaryJson)
                        }
                        val termId = nextId++
                        batch.add(
                            id = termId,
                            expression = expression,
                            reading = reading,
                            definitionTags = row.string(2).orEmpty(),
                            rules = row.string(3).orEmpty(),
                            score = row.number(4),
                            glossaryBlock = glossaries.blockId,
                            glossaryOffset = glossaries.offset,
                            glossaryLength = glossaries.size,
                            sequence = if (v3) row.long(6) else null,
                            termTags = if (v3) row.string(7).orEmpty() else "",
                            expressionReverse = if (index.prefixWildcardsSupported) reverse(expression) else "",
                            readingReverse = if (index.prefixWildcardsSupported) reverse(reading) else "",
                        )
                        fts.add(
                            id = termId,
                            expression = FtsText.indexText(expression),
                            reading = FtsText.indexText(reading),
                            gloss = FtsText.indexText(glossaryText),
                        )
                        bankCount++
                        if (batch.size == batchSize) {
                            flush(batch)
                            flush(fts)
                        }
                    }
                }
                total += bankCount
                onProgress("$bank: $bankCount terms")
                flush(batch)
                flush(fts)
            }
        }
        return total
    }

    private fun importTermMeta(archive: YomitanArchive, dictionaryId: Long): Int {
        val batch = TermMetaBatch(batchSize, dictionaryId)
        var nextId = nextId("term_meta")
        var total = 0
        for (bank in archive.bankNames("term_meta_bank_")) {
            var bankCount = 0
            archive.forEachBankRow(bank) { row ->
                val expression = row.string(0)
                val mode = row.string(1)
                if (expression != null && mode != null) {
                    batch.add(nextId++, expression, mode, if (row.has(2)) row.raw(2) else "null")
                    bankCount++
                    if (batch.size == batchSize) flush(batch)
                }
            }
            total += bankCount
            onProgress("$bank: $bankCount term metadata")
            flush(batch)
        }
        return total
    }

    private fun importKanji(archive: YomitanArchive, dictionaryId: Long, index: YomitanIndex): Int {
        val batch = KanjiBatch(batchSize, dictionaryId)
        val fts = KanjiFtsBatch(batchSize)
        val v3 = index.formatVersion != 1
        var nextId = nextId("kanji")
        var total = 0
        for (bank in archive.bankNames("kanji_bank_")) {
            var bankCount = 0
            archive.forEachBankRow(bank) { row ->
                val character = row.string(0)
                if (!character.isNullOrEmpty()) {
                    val meaningsJson = if (v3 && row.isArray(4)) row.raw(4) else arrayFrom(row, 4)
                    val meaningsText = if (v3 && row.isArray(4)) {
                        Glossary.text(row.source, row.start(4), row.end(4))
                    } else {
                        Glossary.text(meaningsJson)
                    }
                    val kanjiId = nextId++
                    batch.add(
                        id = kanjiId,
                        character = character,
                        onyomi = row.string(1).orEmpty(),
                        kunyomi = row.string(2).orEmpty(),
                        tags = row.string(3).orEmpty(),
                        meaningsJson = meaningsJson,
                        statsJson = if (v3 && row.isObject(5)) row.raw(5) else "{}",
                    )
                    fts.add(kanjiId, FtsText.indexText(character), FtsText.indexText(meaningsText))
                    bankCount++
                    if (batch.size == batchSize) {
                        flush(batch)
                        flush(fts)
                    }
                }
            }
            total += bankCount
            onProgress("$bank: $bankCount kanji")
            flush(batch)
            flush(fts)
        }
        return total
    }

    private fun importKanjiMeta(archive: YomitanArchive, dictionaryId: Long): Int {
        val batch = KanjiMetaBatch(batchSize, dictionaryId)
        var nextId = nextId("kanji_meta")
        var total = 0
        for (bank in archive.bankNames("kanji_meta_bank_")) {
            var bankCount = 0
            archive.forEachBankRow(bank) { row ->
                val character = row.string(0)
                val mode = row.string(1)
                if (character != null && mode != null) {
                    batch.add(nextId++, character, mode, if (row.has(2)) row.raw(2) else "null")
                    bankCount++
                    if (batch.size == batchSize) flush(batch)
                }
            }
            total += bankCount
            onProgress("$bank: $bankCount kanji metadata")
            flush(batch)
        }
        return total
    }

    private fun importMedia(archive: YomitanArchive, dictionaryId: Long): Int {
        val names = archive.mediaNames()
        if (names.isEmpty()) return 0
        val batch = MediaBatch(batchSize, dictionaryId)
        var nextId = nextId("media")
        for (name in names) {
            val bytes = archive.readBytes(name)
            val size = ImageSize.read(bytes)
            batch.add(nextId++, name, mimeType(name), size?.first, size?.second, bytes)
            if (batch.size == batchSize) flush(batch)
        }
        flush(batch)
        onProgress("Imported ${names.size} media files")
        return names.size
    }

    private fun flush(batch: ColumnBatch) {
        if (batch.size == 0) return
        database.executeBatch(batch.sql, batch.size) { row, arguments -> batch.bind(row, arguments) }
        batch.clear()
    }

    /** JSON array assembled from columns [from] onwards, used by format 1 banks. */
    private fun arrayFrom(row: JsonRow, from: Int): String {
        if (row.columnCount <= from) return "[]"
        val builder = StringBuilder(128)
        builder.append('[')
        for (column in from until row.columnCount) {
            if (column > from) builder.append(',')
            builder.append(row.raw(column))
        }
        builder.append(']')
        return builder.toString()
    }

    private fun needsReverseIndexes(): Boolean =
        (database.query(
            "SELECT COUNT(*) FROM dictionaries WHERE prefixWildcardsSupported = 1",
        ).first().first() as Number).toInt() > 0

    private fun nextId(table: String): Long =
        (database.query("SELECT COALESCE(MAX(id), 0) + 1 FROM $table").first().first() as Number).toLong()

    private fun reverse(value: String): String {
        val builder = StringBuilder(value.length)
        var index = value.length
        while (index > 0) {
            val codePoint = value.codePointBefore(index)
            builder.appendCodePoint(codePoint)
            index -= Character.charCount(codePoint)
        }
        return builder.toString()
    }

    private fun mimeType(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "apng", "png" -> "image/png"
        "avif" -> "image/avif"
        "bmp" -> "image/bmp"
        "gif" -> "image/gif"
        "ico" -> "image/x-icon"
        "jpeg", "jpg" -> "image/jpeg"
        "svg" -> "image/svg+xml"
        "tga" -> "image/x-tga"
        "tif", "tiff" -> "image/tiff"
        "webp" -> "image/webp"
        else -> "application/octet-stream"
    }

    private fun countsJson(counts: Map<String, Int>): String = buildJsonObject {
        counts.forEach { (key, value) -> put(key, value) }
    }.toString()

    private fun Boolean.asInt(): Int = if (this) 1 else 0
}
