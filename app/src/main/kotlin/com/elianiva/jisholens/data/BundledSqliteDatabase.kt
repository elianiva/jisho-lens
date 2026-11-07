package com.elianiva.jisholens.data

import androidx.sqlite.SQLITE_DATA_BLOB
import androidx.sqlite.SQLITE_DATA_FLOAT
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_TEXT
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import com.elianiva.jisholens.dictionary.SqliteDatabase
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Adapts androidx.sqlite's bundled connection to the small SQLite surface that
 * :dictionary uses. The connection is not thread-safe, so every call takes the
 * same lock and the repository also confines calls to one dispatcher.
 */
class BundledSqliteDatabase(private val connection: SQLiteConnection) : SqliteDatabase {
    private val lock = ReentrantLock()
    private var transactionDepth = 0

    override fun execute(sql: String, arguments: List<Any?>) = lock.withLock {
        executeLocked(sql, arguments)
    }

    override fun executeBatch(
        sql: String,
        rowCount: Int,
        bind: (row: Int, arguments: MutableList<Any?>) -> Unit,
    ) = lock.withLock {
        if (rowCount == 0) return@withLock
        connection.prepare(sql).use { statement ->
            val arguments = ArrayList<Any?>(16)
            for (row in 0 until rowCount) {
                arguments.clear()
                bind(row, arguments)
                bindArguments(statement, arguments)
                statement.step()
                statement.reset()
                statement.clearBindings()
            }
        }
    }

    override fun query(sql: String, arguments: List<Any?>): List<List<Any?>> = lock.withLock {
        connection.prepare(sql).use { statement ->
            bindArguments(statement, arguments)
            buildList {
                while (statement.step()) {
                    add(
                        (0 until statement.getColumnCount()).map { index ->
                            if (statement.isNull(index)) {
                                null
                            } else {
                                when (statement.getColumnType(index)) {
                                    SQLITE_DATA_INTEGER -> statement.getLong(index)
                                    SQLITE_DATA_FLOAT -> statement.getDouble(index)
                                    SQLITE_DATA_TEXT -> statement.getText(index)
                                    SQLITE_DATA_BLOB -> statement.getBlob(index)
                                    else -> null
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    override fun <T> transaction(block: () -> T): T {
        lock.lock()
        try {
            if (transactionDepth > 0) {
                transactionDepth++
                try {
                    return block()
                } finally {
                    transactionDepth--
                }
            }
            executeLocked("BEGIN IMMEDIATE")
            transactionDepth = 1
            return try {
                val result = block()
                executeLocked("COMMIT")
                result
            } catch (error: Throwable) {
                runCatching { executeLocked("ROLLBACK") }
                throw error
            } finally {
                transactionDepth = 0
            }
        } finally {
            lock.unlock()
        }
    }

    private fun executeLocked(sql: String, arguments: List<Any?> = emptyList()) {
        connection.prepare(sql).use { statement ->
            bindArguments(statement, arguments)
            statement.step()
        }
    }

    private fun bindArguments(statement: SQLiteStatement, arguments: List<Any?>) {
        arguments.forEachIndexed { index, value ->
            val position = index + 1
            when (value) {
                null -> statement.bindNull(position)
                is ByteArray -> statement.bindBlob(position, value)
                is Boolean -> statement.bindLong(position, if (value) 1 else 0)
                is Int -> statement.bindLong(position, value.toLong())
                is Long -> statement.bindLong(position, value)
                is Float -> statement.bindDouble(position, value.toDouble())
                is Double -> statement.bindDouble(position, value)
                is String -> statement.bindText(position, value)
                else -> statement.bindText(position, value.toString())
            }
        }
    }
}
