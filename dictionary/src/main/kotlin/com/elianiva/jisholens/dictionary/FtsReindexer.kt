package com.elianiva.jisholens.dictionary

/**
 * Rebuilds the term search index of one dictionary from the glossaries already stored
 * in the database, so a change to how glosses are indexed reaches an imported
 * dictionary without downloading or importing it again.
 */
class FtsReindexer(
    private val database: SqliteDatabase,
    private val batchSize: Int = 2_000,
) {
    fun reindex(dictionaryId: Long): Int = database.transaction {
        database.execute(
            "DELETE FROM terms_fts WHERE rowid IN (SELECT id FROM terms WHERE dictionaryId = ?)",
            listOf(dictionaryId),
        )
        val batch = TermFtsBatch(batchSize)
        var total = 0
        val blockIds = database.query(
            "SELECT id FROM glossary_blocks WHERE dictionaryId = ? ORDER BY id",
            listOf(dictionaryId),
        ).map { it[0] as Long }
        for (blockId in blockIds) {
            val data = database.query("SELECT data FROM glossary_blocks WHERE id = ?", listOf(blockId))
                .firstOrNull()?.get(0) as? ByteArray ?: continue
            val block = JsonCompression.inflate(data)
            val rows = database.query(
                "SELECT id, expression, reading, glossaryOffset, glossaryLength FROM terms " +
                    "WHERE dictionaryId = ? AND glossaryBlock = ?",
                listOf(dictionaryId, blockId),
            )
            for (row in rows) {
                val json = String(block, (row[3] as Number).toInt(), (row[4] as Number).toInt(), Charsets.UTF_8)
                batch.add(
                    id = row[0] as Long,
                    expression = FtsText.indexText(row[1] as String),
                    reading = FtsText.indexText(row[2] as String),
                    gloss = FtsText.indexText(Glossary.text(json)),
                )
                total++
                if (batch.size == batchSize) flush(batch)
            }
            flush(batch)
        }
        total
    }

    private fun flush(batch: TermFtsBatch) {
        if (batch.size == 0) return
        database.executeBatch(batch.sql, batch.size) { row, arguments -> batch.bind(row, arguments) }
        batch.clear()
    }
}
