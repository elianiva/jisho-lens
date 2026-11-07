package com.elianiva.jisholens.dictionary.cli

import com.elianiva.jisholens.dictionary.DictionaryLookup
import com.elianiva.jisholens.dictionary.DictionarySchema
import com.elianiva.jisholens.dictionary.DictionaryStore
import com.elianiva.jisholens.dictionary.KanjiResult
import com.elianiva.jisholens.dictionary.TermResult
import com.elianiva.jisholens.dictionary.YomitanArchive
import com.elianiva.jisholens.dictionary.YomitanImporter
import com.elianiva.jisholens.dictionary.jdbc.JdbcSqliteDatabase
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.util.Locale
import kotlin.system.exitProcess

private const val DEFAULT_DATABASE = "jisho_lens.db"

private class CliException(message: String) : Exception(message)

private class Options {
    var database = File(DEFAULT_DATABASE)
    var json = false
    var limit = 50
    var alias: String? = null
    var vacuum = false
}

fun main(arguments: Array<String>) {
    exitProcess(execute(arguments))
}

private fun execute(arguments: Array<String>): Int {
    val options = Options()
    val positionals = try {
        parseArguments(arguments, options)
    } catch (error: CliException) {
        System.err.println("error: ${error.message}")
        return 2
    }

    val command = positionals.firstOrNull()
    if (command == null) {
        printUsage()
        return 2
    }

    return try {
        when (command) {
            "import" -> importDictionaries(positionals.drop(1), options)
            "list" -> listDictionaries(options)
            "remove" -> removeDictionary(positionals.getOrNull(1), options)
            "lookup" -> lookupTerms(positionals.drop(1), options)
            "search" -> searchTerms(positionals.drop(1), options)
            "kanji" -> lookupKanji(positionals.drop(1), options)
            "help", "--help", "-h" -> {
                printUsage()
                0
            }
            else -> throw CliException("unknown command '$command'")
        }
    } catch (error: CliException) {
        System.err.println("error: ${error.message}")
        2
    } catch (error: Exception) {
        System.err.println("error: ${error.message}")
        1
    }
}

private fun parseArguments(arguments: Array<String>, options: Options): List<String> {
    val positionals = mutableListOf<String>()
    var index = 0
    while (index < arguments.size) {
        when (val argument = arguments[index]) {
            "--help" -> positionals.add("help")
            "--db" -> {
                val value = arguments.getOrNull(++index) ?: throw CliException("--db needs a path")
                options.database = File(value)
            }
            "--limit" -> {
                val value = arguments.getOrNull(++index) ?: throw CliException("--limit needs a number")
                options.limit = value.toIntOrNull()?.takeIf { it > 0 } ?: throw CliException("--limit needs a positive number")
            }
            "--alias" -> {
                options.alias = arguments.getOrNull(++index) ?: throw CliException("--alias needs a name")
            }
            "--json" -> options.json = true
            "-h" -> positionals.add("help")
            "--vacuum" -> options.vacuum = true
            else -> {
                if (argument.startsWith("--")) throw CliException("unknown option '$argument'")
                positionals.add(argument)
            }
        }
        index++
    }
    return positionals
}

private fun importDictionaries(paths: List<String>, options: Options): Int {
    if (paths.isEmpty()) throw CliException("import needs at least one Yomitan .zip file")
    options.database.parentFile?.mkdirs()
    JdbcSqliteDatabase.open(options.database).use { database ->
        DictionarySchema.ensure(database)
        database.useFastImportSettings()
        val importer = YomitanImporter(database, onProgress = { message -> println(message) })
        for (path in paths) {
            val file = File(path)
            if (!file.isFile) throw CliException("$path is not a file")
            YomitanArchive.open(file).use { archive ->
                val summary = importer.import(archive, options.alias)
                println("Imported ${summary.title} in ${summary.durationMillis}ms")
                summary.counts.forEach { (kind, count) -> println("  $kind: $count") }
            }
        }
        if (options.vacuum) {
            println("Vacuuming ${options.database.path}")
            database.vacuum()
        }
    }
    println("Database: ${options.database.path} (${fileSize(options.database)})")
    return 0
}

private fun listDictionaries(options: Options): Int = withDatabase(options) { database ->
    val dictionaries = DictionaryStore(database).list()
    if (options.json) {
        val payload = buildJsonArray {
            dictionaries.forEach { dictionary ->
                add(
                    buildJsonObject {
                        put("id", dictionary.id)
                        put("title", dictionary.title)
                        put("alias", dictionary.alias)
                        put("revision", dictionary.revision)
                        put("formatVersion", dictionary.formatVersion)
                        put("enabled", dictionary.enabled)
                        put("priority", dictionary.priority)
                        put("importDate", dictionary.importDate)
                        put("counts", buildJsonObject {
                            dictionary.counts.forEach { (kind, count) -> put(kind, count) }
                        })
                    },
                )
            }
        }
        println(payload)
        return@withDatabase 0
    }

    if (dictionaries.isEmpty()) {
        println("No dictionaries imported")
        return@withDatabase 0
    }

    println("%-4s %-8s %-7s %-9s %-7s %s".format("ID", "PRIORITY", "ENABLED", "TERMS", "KANJI", "TITLE"))
    dictionaries.forEach { dictionary ->
        val title = dictionary.alias?.takeIf { it.isNotBlank() } ?: dictionary.title
        val revision = dictionary.revision.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
        println(
            "%-4d %-8d %-7s %-9d %-7d %s%s".format(
                dictionary.id,
                dictionary.priority,
                if (dictionary.enabled) "yes" else "no",
                dictionary.counts["terms"] ?: 0,
                dictionary.counts["kanji"] ?: 0,
                title,
                revision,
            ),
        )
    }
    0
}

private fun removeDictionary(target: String?, options: Options): Int {
    if (target == null) throw CliException("remove needs a dictionary id or title")
    return withDatabase(options) { database ->
        val store = DictionaryStore(database)
        val record = target.toLongOrNull()?.let { store.findById(it) }
            ?: store.findByTitle(target)
            ?: store.list().firstOrNull { it.alias == target }
            ?: throw CliException("no dictionary matches '$target'")
        store.delete(record.id)
        println("Removed ${record.title}")
        0
    }
}

private fun lookupTerms(words: List<String>, options: Options): Int {
    val text = words.joinToString(" ").trim()
    if (text.isEmpty()) throw CliException("lookup needs a word")
    return withDatabase(options) { database ->
        val results = DictionaryLookup(database).lookup(text, options.limit)
        printTerms(results, options.json)
        0
    }
}

private fun searchTerms(words: List<String>, options: Options): Int {
    val query = words.joinToString(" ").trim()
    if (query.isEmpty()) throw CliException("search needs a query")
    return withDatabase(options) { database ->
        val results = DictionaryLookup(database).search(query, options.limit)
        printTerms(results, options.json)
        0
    }
}

private fun lookupKanji(words: List<String>, options: Options): Int {
    val text = words.joinToString(" ").trim()
    if (text.isEmpty()) throw CliException("kanji needs a character or a meaning")
    return withDatabase(options) { database ->
        val lookup = DictionaryLookup(database)
        val results = if (text.codePointCount(0, text.length) == 1) {
            lookup.kanji(text, options.limit)
        } else {
            lookup.searchKanji(text, options.limit)
        }
        printKanji(results, options.json)
        0
    }
}

private fun printTerms(results: List<TermResult>, json: Boolean) {
    if (json) {
        val payload = buildJsonArray {
            results.forEach { term ->
                add(
                    buildJsonObject {
                        put("id", term.termId)
                        put("dictionary", term.dictionaryTitle)
                        put("expression", term.expression)
                        put("reading", term.reading)
                        put("definitionTags", term.definitionTags)
                        put("termTags", term.termTags)
                        put("rules", term.rules)
                        put("score", term.score)
                        put("sequence", term.sequence)
                        put("glossary", term.glossary)
                    },
                )
            }
        }
        println(payload)
        return
    }
    if (results.isEmpty()) {
        println("No results")
        return
    }
    results.forEach { term ->
        val head = if (term.reading == term.expression) term.expression else "${term.expression}【${term.reading}】"
        val details = listOfNotNull(
            term.dictionaryTitle,
            term.definitionTags.takeIf { it.isNotBlank() },
            term.rules.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        println(if (details.isEmpty()) head else "$head  ($details)")
        println("  ${term.glossaryText}")
    }
}

private fun printKanji(results: List<KanjiResult>, json: Boolean) {
    if (json) {
        val payload = buildJsonArray {
            results.forEach { kanji ->
                add(
                    buildJsonObject {
                        put("id", kanji.kanjiId)
                        put("dictionary", kanji.dictionaryTitle)
                        put("character", kanji.character)
                        put("onyomi", kanji.onyomi)
                        put("kunyomi", kanji.kunyomi)
                        put("tags", kanji.tags)
                        put("meanings", kanji.meanings)
                        put("stats", kanji.stats)
                    },
                )
            }
        }
        println(payload)
        return
    }
    if (results.isEmpty()) {
        println("No results")
        return
    }
    results.forEach { kanji ->
        println("${kanji.character}  (${kanji.dictionaryTitle})")
        val readings = listOfNotNull(
            kanji.onyomi.takeIf { it.isNotBlank() }?.let { "on: $it" },
            kanji.kunyomi.takeIf { it.isNotBlank() }?.let { "kun: $it" },
            kanji.tags.takeIf { it.isNotBlank() },
        ).joinToString("  ")
        if (readings.isNotEmpty()) println("  $readings")
        println("  ${kanji.meaningsText}")
    }
}

private inline fun <T> withDatabase(options: Options, block: (JdbcSqliteDatabase) -> T): T {
    if (!options.database.isFile) {
        throw CliException("${options.database.path} does not exist; import a dictionary first")
    }
    return JdbcSqliteDatabase.open(options.database).use { database ->
        DictionarySchema.ensure(database)
        block(database)
    }
}

private fun fileSize(file: File): String {
    val bytes = file.length().toDouble()
    return when {
        bytes >= 1024 * 1024 * 1024 -> String.format(Locale.ROOT, "%.2f GB", bytes / (1024 * 1024 * 1024))
        bytes >= 1024 * 1024 -> String.format(Locale.ROOT, "%.1f MB", bytes / (1024 * 1024))
        bytes >= 1024 -> String.format(Locale.ROOT, "%.1f KB", bytes / 1024)
        else -> "$bytes bytes"
    }
}

private fun printUsage() {
    println(
        """
        jisho-dict - import Yomitan dictionaries into a Jisho Lens SQLite database

        Usage:
          jisho-dict import <dictionary.zip>... [--db <path>] [--alias <name>] [--vacuum]
          jisho-dict list [--db <path>] [--json]
          jisho-dict remove <id|title> [--db <path>]
          jisho-dict lookup <word> [--db <path>] [--limit <n>] [--json]
          jisho-dict search <query> [--db <path>] [--limit <n>] [--json]
          jisho-dict kanji <character> [--db <path>] [--limit <n>] [--json]

        Options:
          --db      Database file to read or write (default: $DEFAULT_DATABASE)
          --limit   Maximum number of results (default: 50)
          --alias   Display name for the imported dictionary
          --vacuum  Compact the database file after importing
          --json    Print results as JSON
        """.trimIndent(),
    )
}
