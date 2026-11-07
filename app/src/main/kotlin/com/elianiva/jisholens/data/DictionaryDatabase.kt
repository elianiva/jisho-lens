package com.elianiva.jisholens.data

import android.content.Context
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.elianiva.jisholens.dictionary.DictionarySchema
import com.elianiva.jisholens.dictionary.SqliteDatabase
import java.io.File

/** Owns the single connection to the imported dictionary database. */
class DictionaryDatabase(context: Context) {
    private val file: File = context.getDatabasePath(DB_NAME)
    private val driver = BundledSQLiteDriver()

    val sqlite: SqliteDatabase by lazy { open() }

    private fun open(): SqliteDatabase {
        file.parentFile?.mkdirs()
        val connection = driver.open(file.absolutePath)
        return BundledSqliteDatabase(connection).also { database ->
            database.execute("PRAGMA journal_mode = WAL")
            database.execute("PRAGMA synchronous = NORMAL")
            database.execute("PRAGMA busy_timeout = 5000")
            database.execute("PRAGMA cache_size = -32000")
            database.execute("PRAGMA temp_store = MEMORY")
            DictionarySchema.ensure(database)
        }
    }

    fun fileSize(): Long = file.length()

    fun exists(): Boolean = file.exists()

    companion object {
        const val DB_NAME = "jisho.db"
    }
}
