package com.elianiva.jisholens.dictionary

import com.elianiva.jisholens.dictionary.jdbc.JdbcSqliteDatabase
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object Fixtures {
    fun archive(vararg files: Pair<String, String>): YomitanArchive =
        archive(files.associate { (name, content) -> name to content.toByteArray(Charsets.UTF_8) })

    fun archive(files: Map<String, ByteArray>): YomitanArchive {
        val file = Files.createTempFile("dictionary", ".zip").toFile()
        file.deleteOnExit()
        ZipOutputStream(file.outputStream()).use { zip ->
            for ((name, bytes) in files) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return YomitanArchive.open(file)
    }

    fun database(): JdbcSqliteDatabase {
        val file = Files.createTempFile("dictionary", ".db").toFile()
        file.deleteOnExit()
        val database = JdbcSqliteDatabase.open(file)
        DictionarySchema.create(database)
        return database
    }

    fun png(width: Int, height: Int): ByteArray {
        val bytes = ByteArray(24)
        bytes[0] = 0x89.toByte()
        bytes[1] = 'P'.code.toByte()
        bytes[2] = 'N'.code.toByte()
        bytes[3] = 'G'.code.toByte()
        bytes[12] = 'I'.code.toByte()
        bytes[13] = 'H'.code.toByte()
        bytes[14] = 'D'.code.toByte()
        bytes[15] = 'R'.code.toByte()
        writeBigEndian(bytes, 16, width)
        writeBigEndian(bytes, 20, height)
        return bytes
    }

    fun gif(width: Int, height: Int): ByteArray {
        val bytes = ByteArray(12)
        bytes[0] = 'G'.code.toByte()
        bytes[1] = 'I'.code.toByte()
        bytes[2] = 'F'.code.toByte()
        bytes[3] = '8'.code.toByte()
        bytes[4] = '9'.code.toByte()
        bytes[5] = 'a'.code.toByte()
        bytes[6] = (width and 0xFF).toByte()
        bytes[7] = (width shr 8).toByte()
        bytes[8] = (height and 0xFF).toByte()
        bytes[9] = (height shr 8).toByte()
        return bytes
    }

    private fun writeBigEndian(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value shr 24).toByte()
        bytes[offset + 1] = (value shr 16).toByte()
        bytes[offset + 2] = (value shr 8).toByte()
        bytes[offset + 3] = value.toByte()
    }
}
