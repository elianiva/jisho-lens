package com.elianiva.jisholens.dictionary

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** A row of the `dictionaries` table. */
data class DictionaryRecord(
    val id: Long,
    val title: String,
    val revision: String,
    val formatVersion: Int,
    val sequenced: Boolean,
    val enabled: Boolean,
    val priority: Int,
    val alias: String?,
    val importDate: Long,
    val importSuccess: Boolean,
    val prefixWildcardsSupported: Boolean,
    val counts: Map<String, Int>,
)

/** Dictionary-level operations: listing, enabling, reordering, and removal. */
class DictionaryStore(private val database: SqliteDatabase) {
    fun list(): List<DictionaryRecord> = database.query(
        """
        SELECT id, title, revision, formatVersion, sequenced, enabled, priority, alias, importDate,
               importSuccess, prefixWildcardsSupported, countsJson
        FROM dictionaries
        ORDER BY priority ASC, title ASC
        """,
    ).map { row ->
        DictionaryRecord(
            id = row[0] as Long,
            title = row[1] as String,
            revision = row[2] as String,
            formatVersion = (row[3] as Number).toInt(),
            sequenced = (row[4] as Number).toInt() != 0,
            enabled = (row[5] as Number).toInt() != 0,
            priority = (row[6] as Number).toInt(),
            alias = row[7] as String?,
            importDate = row[8] as Long,
            importSuccess = (row[9] as Number).toInt() != 0,
            prefixWildcardsSupported = (row[10] as Number).toInt() != 0,
            counts = parseCounts(row[11] as String?),
        )
    }

    fun findById(id: Long): DictionaryRecord? = list().firstOrNull { it.id == id }

    fun findByTitle(title: String): DictionaryRecord? = list().firstOrNull { it.title == title }

    fun setEnabled(id: Long, enabled: Boolean) {
        database.execute(
            "UPDATE dictionaries SET enabled = ? WHERE id = ?",
            listOf(if (enabled) 1 else 0, id),
        )
    }

    /** Deletes a dictionary and every row it owns, including its FTS rows. */
    fun delete(id: Long) {
        database.transaction {
            database.execute(
                "DELETE FROM terms_fts WHERE rowid IN (SELECT id FROM terms WHERE dictionaryId = ?)",
                listOf(id),
            )
            database.execute(
                "DELETE FROM kanji_fts WHERE rowid IN (SELECT id FROM kanji WHERE dictionaryId = ?)",
                listOf(id),
            )
            database.execute("DELETE FROM glossary_blocks WHERE dictionaryId = ?", listOf(id))
            database.execute("DELETE FROM terms WHERE dictionaryId = ?", listOf(id))
            database.execute("DELETE FROM kanji WHERE dictionaryId = ?", listOf(id))
            database.execute("DELETE FROM term_meta WHERE dictionaryId = ?", listOf(id))
            database.execute("DELETE FROM kanji_meta WHERE dictionaryId = ?", listOf(id))
            database.execute("DELETE FROM tags WHERE dictionaryId = ?", listOf(id))
            database.execute("DELETE FROM media WHERE dictionaryId = ?", listOf(id))
            database.execute("DELETE FROM dictionaries WHERE id = ?", listOf(id))
        }
    }

    fun removeByTitle(title: String): Boolean {
        val record = findByTitle(title) ?: return false
        delete(record.id)
        return true
    }

    private fun parseCounts(value: String?): Map<String, Int> {
        if (value.isNullOrBlank()) return emptyMap()
        val objectValue = Json.parseToJsonElement(value) as? JsonObject ?: return emptyMap()
        return objectValue.mapNotNull { (key, valueElement) ->
            val count = (valueElement as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
            if (count == null) null else key to count
        }.toMap()
    }
}
