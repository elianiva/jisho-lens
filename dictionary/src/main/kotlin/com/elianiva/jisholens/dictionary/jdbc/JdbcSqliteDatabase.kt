package com.elianiva.jisholens.dictionary.jdbc

import com.elianiva.jisholens.dictionary.SqliteDatabase
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement

/** [SqliteDatabase] backed by xerial's JDBC driver, used by the CLI. */
class JdbcSqliteDatabase private constructor(private val connection: Connection) : SqliteDatabase, AutoCloseable {
    private var transactionDepth = 0

    override fun execute(sql: String, arguments: List<Any?>) {
        connection.prepareStatement(sql).use { statement ->
            bindArguments(statement, arguments)
            statement.execute()
        }
    }

    override fun executeBatch(sql: String, rowCount: Int, bind: (row: Int, arguments: MutableList<Any?>) -> Unit) {
        if (rowCount == 0) return
        connection.prepareStatement(sql).use { statement ->
            val arguments = ArrayList<Any?>(16)
            for (row in 0 until rowCount) {
                arguments.clear()
                bind(row, arguments)
                bindArguments(statement, arguments)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }

    override fun query(sql: String, arguments: List<Any?>): List<List<Any?>> {
        connection.prepareStatement(sql).use { statement ->
            bindArguments(statement, arguments)
            statement.executeQuery().use { resultSet ->
                val columnCount = resultSet.metaData.columnCount
                val rows = mutableListOf<List<Any?>>()
                while (resultSet.next()) {
                    rows.add((1..columnCount).map { normalize(resultSet.getObject(it)) })
                }
                return rows
            }
        }
    }

    override fun <T> transaction(block: () -> T): T {
        if (transactionDepth > 0) {
            transactionDepth++
            try {
                return block()
            } finally {
                transactionDepth--
            }
        }
        connection.autoCommit = false
        transactionDepth = 1
        try {
            val result = block()
            connection.commit()
            return result
        } catch (error: Throwable) {
            connection.rollback()
            throw error
        } finally {
            transactionDepth = 0
            connection.autoCommit = true
        }
    }

    /** Turns off the durability guarantees that make a bulk import slow. */
    fun useFastImportSettings() {
        execute("PRAGMA journal_mode = MEMORY")
        execute("PRAGMA synchronous = OFF")
        execute("PRAGMA temp_store = MEMORY")
        execute("PRAGMA cache_size = -64000")
        execute("PRAGMA locking_mode = EXCLUSIVE")
    }

    fun vacuum() {
        execute("VACUUM")
    }

    override fun close() {
        connection.close()
    }

    private fun bindArguments(statement: PreparedStatement, arguments: List<Any?>) {
        arguments.forEachIndexed { index, argument ->
            when (argument) {
                null -> statement.setObject(index + 1, null)
                is ByteArray -> statement.setBytes(index + 1, argument)
                is Boolean -> statement.setInt(index + 1, if (argument) 1 else 0)
                else -> statement.setObject(index + 1, argument)
            }
        }
    }

    private fun normalize(value: Any?): Any? = when (value) {
        is Int -> value.toLong()
        is Short -> value.toLong()
        is Float -> value.toDouble()
        else -> value
    }

    companion object {
        fun open(file: File): JdbcSqliteDatabase {
            Class.forName("org.sqlite.JDBC")
            return JdbcSqliteDatabase(DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}"))
        }
    }
}
