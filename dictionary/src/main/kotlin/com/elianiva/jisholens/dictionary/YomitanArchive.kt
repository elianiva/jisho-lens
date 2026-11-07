package com.elianiva.jisholens.dictionary

import kotlinx.serialization.json.Json
import java.io.Closeable
import java.io.File
import java.util.zip.ZipFile

/**
 * Reads a Yomitan dictionary archive. The archive is the interchange format, so
 * nothing here depends on Yomitan's internal IndexedDB files.
 */
class YomitanArchive private constructor(private val zipFile: ZipFile) : Closeable {
    private val json = Json

    private val entries: List<String> by lazy {
        zipFile.entries().asSequence().filterNot { it.isDirectory }.map { it.name }.toList()
    }

    fun readIndex(): YomitanIndex {
        val indexJson = readText("index.json")
            ?: throw YomitanFormatException("index.json is missing")
        return YomitanParsing.index(json.parseToJsonElement(indexJson), stylesCss())
    }

    /** Bank files for a prefix such as `term_bank_`, ordered by their numeric suffix. */
    fun bankNames(prefix: String): List<String> = entries
        .filter { it.startsWith(prefix) && it.endsWith(".json") }
        .filter { it.substring(prefix.length).removeSuffix(".json").toIntOrNull() != null }
        .sortedBy { it.substring(prefix.length).removeSuffix(".json").toInt() }

    /**
     * Streams the rows of one bank file. The file is read whole so the scanner
     * can hand out offsets instead of substrings; the largest Yomitan banks are
     * a few tens of megabytes.
     */
    fun forEachBankRow(name: String, action: (JsonRow) -> Unit) {
        val entry = zipFile.getEntry(name) ?: throw YomitanFormatException("$name is missing")
        val text = zipFile.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
        JsonArrays.forEachElement(text, JsonRow(text)) { action(it) }
    }

    fun stylesCss(): String? = readText("styles.css")

    fun mediaNames(): List<String> = entries.filter { name ->
        name.substringAfterLast('.', "").lowercase() in mediaExtensions
    }

    fun readBytes(name: String): ByteArray {
        val entry = zipFile.getEntry(name) ?: throw YomitanFormatException("$name is missing")
        return zipFile.getInputStream(entry).use { it.readBytes() }
    }

    private fun readText(name: String): String? {
        val entry = zipFile.getEntry(name) ?: return null
        return zipFile.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
    }

    override fun close() {
        zipFile.close()
    }

    companion object {
        private val mediaExtensions = setOf(
            "apng", "avif", "bmp", "gif", "ico", "jpeg", "jpg", "png", "svg", "tga", "tif", "tiff", "webp",
        )

        fun open(file: File): YomitanArchive = YomitanArchive(ZipFile(file))
    }
}
