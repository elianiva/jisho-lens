package com.elianiva.jisholens.dictionary

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** A row of the `tags` table. */
data class TagRecord(
    val dictionaryId: Long,
    val name: String,
    val category: String,
    val sortOrder: Int,
    val notes: String,
    val score: Double,
)

/** A row of the `term_meta` table. [data] is the original JSON value. */
data class TermMetaRecord(
    val dictionaryId: Long,
    val expression: String,
    val mode: String,
    val data: String,
)

/** A row of the `media` table. */
class MediaRecord(
    val dictionaryId: Long,
    val path: String,
    val mediaType: String,
    val width: Int?,
    val height: Int?,
    val data: ByteArray,
)

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

    fun setPriority(id: Long, priority: Int) {
        database.execute("UPDATE dictionaries SET priority = ? WHERE id = ?", listOf(priority, id))
    }

    fun setAlias(id: Long, alias: String?) {
        database.execute("UPDATE dictionaries SET alias = ? WHERE id = ?", listOf(alias, id))
    }

    /** Swaps this dictionary with its neighbour in the current order. */
    fun move(id: Long, delta: Int): Boolean {
        val records = list()
        val index = records.indexOfFirst { it.id == id }
        if (index < 0) return false
        val targetIndex = index + delta
        if (targetIndex !in records.indices) return false
        val current = records[index]
        val target = records[targetIndex]
        database.transaction {
            setPriority(current.id, target.priority)
            setPriority(target.id, current.priority)
        }
        return true
    }

    fun styles(id: Long): String? {
        val rows = database.query("SELECT styles FROM dictionaries WHERE id = ?", listOf(id))
        return rows.firstOrNull()?.firstOrNull() as String?
    }

    fun tags(dictionaryId: Long? = null): List<TagRecord> {
        val rows = if (dictionaryId == null) {
            database.query(
                "SELECT dictionaryId, name, category, sortOrder, notes, score FROM tags ORDER BY dictionaryId, sortOrder, name",
            )
        } else {
            database.query(
                "SELECT dictionaryId, name, category, sortOrder, notes, score FROM tags WHERE dictionaryId = ? ORDER BY sortOrder, name",
                listOf(dictionaryId),
            )
        }
        return rows.map { row ->
            TagRecord(
                dictionaryId = (row[0] as Number).toLong(),
                name = row[1] as String,
                category = row[2] as String,
                sortOrder = (row[3] as Number).toInt(),
                notes = row[4] as String,
                score = (row[5] as Number).toDouble(),
            )
        }
    }

    /** Returns term metadata for the expressions, in one query per 400 keys. */
    fun termMeta(expressions: Collection<String>): List<TermMetaRecord> {
        if (expressions.isEmpty()) return emptyList()
        val seen = LinkedHashSet(expressions)
        val result = ArrayList<TermMetaRecord>()
        for (chunk in seen.chunked(400)) {
            val placeholders = chunk.joinToString(", ") { "?" }
            val rows = database.query(
                "SELECT dictionaryId, expression, mode, data FROM term_meta WHERE expression IN ($placeholders)",
                chunk,
            )
            rows.mapTo(result) { row ->
                TermMetaRecord(
                    dictionaryId = (row[0] as Number).toLong(),
                    expression = row[1] as String,
                    mode = row[2] as String,
                    data = row[3] as String,
                )
            }
        }
        return result
    }

    fun media(dictionaryId: Long, path: String): MediaRecord? {
        val rows = database.query(
            "SELECT dictionaryId, path, mediaType, width, height, data FROM media WHERE dictionaryId = ? AND path = ? LIMIT 1",
            listOf(dictionaryId, path),
        )
        val row = rows.firstOrNull() ?: return null
        return MediaRecord(
            dictionaryId = (row[0] as Number).toLong(),
            path = row[1] as String,
            mediaType = row[2] as String,
            width = (row[3] as Number?)?.toInt(),
            height = (row[4] as Number?)?.toInt(),
            data = row[5] as ByteArray,
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
