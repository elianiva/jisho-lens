package com.elianiva.jisholens.dictionary

/**
 * The subset of SQLite that the dictionary code needs.
 *
 * The importer and lookup own all SQL so that every platform runs the same
 * statements. The CLI implements this with JDBC; the app can implement it with
 * `androidx.sqlite` (or Room's support database) without changing this module.
 */
interface SqliteDatabase {
    fun execute(sql: String, arguments: List<Any?> = emptyList())

    /**
     * Runs one prepared statement once per row, as a JDBC-style batch.
     *
     * The callback fills a reused list instead of returning a row list, so a
     * bulk import does not allocate a collection per row. Values are copied by
     * the implementation before the callback runs again.
     */
    fun executeBatch(sql: String, rowCount: Int, bind: (row: Int, arguments: MutableList<Any?>) -> Unit)

    /** Returns rows in the order of the selected columns. */
    fun query(sql: String, arguments: List<Any?> = emptyList()): List<List<Any?>>

    /**
     * Runs [block] in a transaction. Nested calls join the outer transaction,
     * so the importer can wrap a whole dictionary and the store can still wrap
     * its own writes.
     */
    fun <T> transaction(block: () -> T): T
}
