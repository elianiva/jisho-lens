package com.elianiva.jisholens.dictionary

/**
 * The database layout. It mirrors Yomitan's object stores instead of inventing
 * a new dictionary model, so an imported archive keeps its tags, sequence
 * groups, metadata, and media.
 *
 * Three deliberate choices:
 *  - Glossary JSON lives in 4 MiB zlib-compressed blocks (`glossary_blocks`),
 *    and each term stores its block, offset, and length. Per-row compression
 *    added a stream header to every entry and compressed much worse.
 *  - `terms_fts` and `kanji_fts` are FTS4 tables with `unicode61`, available in
 *    Android's bundled SQLite. FTS5 is not.
 *  - CJK text is indexed one character per token, so partial Japanese input can
 *    still match. See [FtsText].
 *
 * Indexes are created separately from tables: a bulk import drops them first
 * and rebuilds them once at the end, which is much faster than maintaining six
 * b-trees row by row.
 */
object DictionarySchema {
    const val VERSION = 1

    val tableStatements: List<String> = listOf(
        """
        CREATE TABLE IF NOT EXISTS dictionaries (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT NOT NULL,
            revision TEXT NOT NULL DEFAULT '',
            formatVersion INTEGER NOT NULL DEFAULT 1,
            sequenced INTEGER NOT NULL DEFAULT 0,
            enabled INTEGER NOT NULL DEFAULT 1,
            priority INTEGER NOT NULL DEFAULT 0,
            alias TEXT,
            importDate INTEGER NOT NULL,
            importSuccess INTEGER NOT NULL DEFAULT 1,
            prefixWildcardsSupported INTEGER NOT NULL DEFAULT 0,
            styles TEXT,
            frequencyMode TEXT,
            sourceLanguage TEXT,
            targetLanguage TEXT,
            author TEXT,
            url TEXT,
            description TEXT,
            attribution TEXT,
            isUpdatable INTEGER,
            indexUrl TEXT,
            downloadUrl TEXT,
            countsJson TEXT
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS terms (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            dictionaryId INTEGER NOT NULL,
            expression TEXT NOT NULL,
            reading TEXT NOT NULL,
            definitionTags TEXT NOT NULL DEFAULT '',
            rules TEXT NOT NULL DEFAULT '',
            score REAL NOT NULL DEFAULT 0,
            glossaryBlock INTEGER NOT NULL,
            glossaryOffset INTEGER NOT NULL,
            glossaryLength INTEGER NOT NULL,
            sequence INTEGER,
            termTags TEXT NOT NULL DEFAULT '',
            expressionReverse TEXT NOT NULL DEFAULT '',
            readingReverse TEXT NOT NULL DEFAULT ''
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS glossary_blocks (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            dictionaryId INTEGER NOT NULL,
            data BLOB NOT NULL
        )
        """,
        """
        CREATE VIRTUAL TABLE IF NOT EXISTS terms_fts USING fts4(
            expression,
            reading,
            gloss,
            tokenize=unicode61
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS term_meta (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            dictionaryId INTEGER NOT NULL,
            expression TEXT NOT NULL,
            mode TEXT NOT NULL,
            data TEXT NOT NULL
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS kanji (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            dictionaryId INTEGER NOT NULL,
            character TEXT NOT NULL,
            onyomi TEXT NOT NULL DEFAULT '',
            kunyomi TEXT NOT NULL DEFAULT '',
            tags TEXT NOT NULL DEFAULT '',
            meanings TEXT NOT NULL DEFAULT '[]',
            stats TEXT NOT NULL DEFAULT '{}'
        )
        """,
        """
        CREATE VIRTUAL TABLE IF NOT EXISTS kanji_fts USING fts4(
            character,
            meanings,
            tokenize=unicode61
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS kanji_meta (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            dictionaryId INTEGER NOT NULL,
            character TEXT NOT NULL,
            mode TEXT NOT NULL,
            data TEXT NOT NULL
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS tags (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            dictionaryId INTEGER NOT NULL,
            name TEXT NOT NULL,
            category TEXT NOT NULL DEFAULT 'default',
            sortOrder INTEGER NOT NULL DEFAULT 0,
            notes TEXT NOT NULL DEFAULT '',
            score REAL NOT NULL DEFAULT 0
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS media (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            dictionaryId INTEGER NOT NULL,
            path TEXT NOT NULL,
            mediaType TEXT NOT NULL,
            width INTEGER,
            height INTEGER,
            data BLOB NOT NULL
        )
        """,
    )

    val indexStatements: List<String> = listOf(
        "CREATE UNIQUE INDEX IF NOT EXISTS index_dictionaries_title ON dictionaries(title)",
        "CREATE INDEX IF NOT EXISTS index_dictionaries_priority ON dictionaries(priority)",
        "CREATE INDEX IF NOT EXISTS index_terms_dictionaryId ON terms(dictionaryId)",
        "CREATE INDEX IF NOT EXISTS index_terms_expression ON terms(expression)",
        "CREATE INDEX IF NOT EXISTS index_terms_reading ON terms(reading)",
        "CREATE INDEX IF NOT EXISTS index_terms_sequence ON terms(sequence)",
        "CREATE INDEX IF NOT EXISTS index_glossary_blocks_dictionaryId ON glossary_blocks(dictionaryId)",
        "CREATE INDEX IF NOT EXISTS index_term_meta_dictionaryId ON term_meta(dictionaryId)",
        "CREATE INDEX IF NOT EXISTS index_term_meta_expression ON term_meta(expression)",
        "CREATE INDEX IF NOT EXISTS index_term_meta_mode ON term_meta(mode)",
        "CREATE INDEX IF NOT EXISTS index_kanji_dictionaryId ON kanji(dictionaryId)",
        "CREATE INDEX IF NOT EXISTS index_kanji_character ON kanji(character)",
        "CREATE INDEX IF NOT EXISTS index_kanji_meta_dictionaryId ON kanji_meta(dictionaryId)",
        "CREATE INDEX IF NOT EXISTS index_kanji_meta_character ON kanji_meta(character)",
        "CREATE INDEX IF NOT EXISTS index_tags_dictionaryId ON tags(dictionaryId)",
        "CREATE INDEX IF NOT EXISTS index_tags_name ON tags(name)",
        "CREATE INDEX IF NOT EXISTS index_media_dictionaryId ON media(dictionaryId)",
        "CREATE INDEX IF NOT EXISTS index_media_path ON media(path)",
    )

    /**
     * Prefix wildcards need the reversed columns to be searchable by suffix.
     * Only dictionaries that declare support pay for these indexes.
     */
    val reverseIndexStatements: List<String> = listOf(
        "CREATE INDEX IF NOT EXISTS index_terms_expressionReverse ON terms(expressionReverse)",
        "CREATE INDEX IF NOT EXISTS index_terms_readingReverse ON terms(readingReverse)",
    )

    private val indexNames: List<String> = (indexStatements + reverseIndexStatements)
        .map { it.substringAfter("INDEX IF NOT EXISTS ").substringBefore(" ON ") }

    fun ensure(database: SqliteDatabase) {
        val existing = database.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'dictionaries'",
        )
        if (existing.isEmpty()) {
            create(database)
        } else {
            createIndexes(database)
        }
    }

    fun create(database: SqliteDatabase) {
        database.transaction {
            tableStatements.forEach { database.execute(it) }
            database.execute("PRAGMA user_version = $VERSION")
            createIndexes(database)
        }
    }

    fun createIndexes(database: SqliteDatabase) {
        indexStatements.forEach { database.execute(it) }
    }

    fun dropIndexes(database: SqliteDatabase) {
        indexNames.forEach { database.execute("DROP INDEX IF EXISTS $it") }
    }

    fun createReverseIndexes(database: SqliteDatabase) {
        reverseIndexStatements.forEach { database.execute(it) }
    }
}
