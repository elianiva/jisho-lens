package com.elianiva.jisholens.dictionary

/**
 * A batch of rows held column by column.
 *
 * An import writes hundreds of thousands of rows. Holding them as a list of
 * argument lists would allocate a list and a boxed value per column per row.
 * Parallel arrays keep the values unboxed and let the writer bind straight from
 * the arrays.
 */
internal interface ColumnBatch {
    val size: Int
    val sql: String

    /** Appends the row at [row] to [arguments], in the order of [sql] placeholders. */
    fun bind(row: Int, arguments: MutableList<Any?>)

    fun clear()
}

internal class TermBatch(
    private val capacity: Int,
    private val dictionaryId: Long,
) : ColumnBatch {
    private val ids = LongArray(capacity)
    private val expressions = arrayOfNulls<String>(capacity)
    private val readings = arrayOfNulls<String>(capacity)
    private val definitionTags = arrayOfNulls<String>(capacity)
    private val rules = arrayOfNulls<String>(capacity)
    private val scores = DoubleArray(capacity)
    private val glossaryBlocks = LongArray(capacity)
    private val glossaryOffsets = IntArray(capacity)
    private val glossaryLengths = IntArray(capacity)
    private val sequences = LongArray(capacity)
    private val hasSequence = BooleanArray(capacity)
    private val termTags = arrayOfNulls<String>(capacity)
    private val expressionReverse = arrayOfNulls<String>(capacity)
    private val readingReverse = arrayOfNulls<String>(capacity)

    override var size = 0
        private set

    override val sql: String = """
        INSERT INTO terms (
            id, dictionaryId, expression, reading, definitionTags, rules, score,
            glossaryBlock, glossaryOffset, glossaryLength,
            sequence, termTags, expressionReverse, readingReverse
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """

    fun add(
        id: Long,
        expression: String,
        reading: String,
        definitionTags: String,
        rules: String,
        score: Double,
        glossaryBlock: Long,
        glossaryOffset: Int,
        glossaryLength: Int,
        sequence: Long?,
        termTags: String,
        expressionReverse: String,
        readingReverse: String,
    ) {
        ids[size] = id
        expressions[size] = expression
        readings[size] = reading
        this.definitionTags[size] = definitionTags
        this.rules[size] = rules
        scores[size] = score
        glossaryBlocks[size] = glossaryBlock
        glossaryOffsets[size] = glossaryOffset
        glossaryLengths[size] = glossaryLength
        sequences[size] = sequence ?: 0L
        hasSequence[size] = sequence != null
        this.termTags[size] = termTags
        this.expressionReverse[size] = expressionReverse
        this.readingReverse[size] = readingReverse
        size++
    }

    override fun bind(row: Int, arguments: MutableList<Any?>) {
        arguments.add(ids[row])
        arguments.add(dictionaryId)
        arguments.add(expressions[row])
        arguments.add(readings[row])
        arguments.add(definitionTags[row])
        arguments.add(rules[row])
        arguments.add(scores[row])
        arguments.add(glossaryBlocks[row])
        arguments.add(glossaryOffsets[row])
        arguments.add(glossaryLengths[row])
        arguments.add(if (hasSequence[row]) sequences[row] else null)
        arguments.add(termTags[row])
        arguments.add(expressionReverse[row])
        arguments.add(readingReverse[row])
    }

    override fun clear() {
        size = 0
    }
}

internal class TermFtsBatch(private val capacity: Int) : ColumnBatch {
    private val ids = LongArray(capacity)
    private val expressions = arrayOfNulls<String>(capacity)
    private val readings = arrayOfNulls<String>(capacity)
    private val glosses = arrayOfNulls<String>(capacity)

    override var size = 0
        private set

    override val sql: String = "INSERT INTO terms_fts (rowid, expression, reading, gloss) VALUES (?, ?, ?, ?)"

    fun add(id: Long, expression: String, reading: String, gloss: String) {
        ids[size] = id
        expressions[size] = expression
        readings[size] = reading
        glosses[size] = gloss
        size++
    }

    override fun bind(row: Int, arguments: MutableList<Any?>) {
        arguments.add(ids[row])
        arguments.add(expressions[row])
        arguments.add(readings[row])
        arguments.add(glosses[row])
    }

    override fun clear() {
        size = 0
    }
}

internal class TermMetaBatch(
    private val capacity: Int,
    private val dictionaryId: Long,
) : ColumnBatch {
    private val ids = LongArray(capacity)
    private val expressions = arrayOfNulls<String>(capacity)
    private val modes = arrayOfNulls<String>(capacity)
    private val data = arrayOfNulls<String>(capacity)

    override var size = 0
        private set

    override val sql: String = "INSERT INTO term_meta (id, dictionaryId, expression, mode, data) VALUES (?, ?, ?, ?, ?)"

    fun add(id: Long, expression: String, mode: String, value: String) {
        ids[size] = id
        expressions[size] = expression
        modes[size] = mode
        data[size] = value
        size++
    }

    override fun bind(row: Int, arguments: MutableList<Any?>) {
        arguments.add(ids[row])
        arguments.add(dictionaryId)
        arguments.add(expressions[row])
        arguments.add(modes[row])
        arguments.add(data[row])
    }

    override fun clear() {
        size = 0
    }
}

internal class KanjiBatch(
    private val capacity: Int,
    private val dictionaryId: Long,
) : ColumnBatch {
    private val ids = LongArray(capacity)
    private val characters = arrayOfNulls<String>(capacity)
    private val onyomi = arrayOfNulls<String>(capacity)
    private val kunyomi = arrayOfNulls<String>(capacity)
    private val tags = arrayOfNulls<String>(capacity)
    private val meanings = arrayOfNulls<String>(capacity)
    private val stats = arrayOfNulls<String>(capacity)

    override var size = 0
        private set

    override val sql: String =
        "INSERT INTO kanji (id, dictionaryId, character, onyomi, kunyomi, tags, meanings, stats) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"

    fun add(
        id: Long,
        character: String,
        onyomi: String,
        kunyomi: String,
        tags: String,
        meaningsJson: String,
        statsJson: String,
    ) {
        ids[size] = id
        characters[size] = character
        this.onyomi[size] = onyomi
        this.kunyomi[size] = kunyomi
        this.tags[size] = tags
        meanings[size] = meaningsJson
        stats[size] = statsJson
        size++
    }

    override fun bind(row: Int, arguments: MutableList<Any?>) {
        arguments.add(ids[row])
        arguments.add(dictionaryId)
        arguments.add(characters[row])
        arguments.add(onyomi[row])
        arguments.add(kunyomi[row])
        arguments.add(tags[row])
        arguments.add(meanings[row])
        arguments.add(stats[row])
    }

    override fun clear() {
        size = 0
    }
}

internal class KanjiFtsBatch(private val capacity: Int) : ColumnBatch {
    private val ids = LongArray(capacity)
    private val characters = arrayOfNulls<String>(capacity)
    private val meanings = arrayOfNulls<String>(capacity)

    override var size = 0
        private set

    override val sql: String = "INSERT INTO kanji_fts (rowid, character, meanings) VALUES (?, ?, ?)"

    fun add(id: Long, character: String, meaningsText: String) {
        ids[size] = id
        characters[size] = character
        meanings[size] = meaningsText
        size++
    }

    override fun bind(row: Int, arguments: MutableList<Any?>) {
        arguments.add(ids[row])
        arguments.add(characters[row])
        arguments.add(meanings[row])
    }

    override fun clear() {
        size = 0
    }
}

internal class KanjiMetaBatch(
    private val capacity: Int,
    private val dictionaryId: Long,
) : ColumnBatch {
    private val ids = LongArray(capacity)
    private val characters = arrayOfNulls<String>(capacity)
    private val modes = arrayOfNulls<String>(capacity)
    private val data = arrayOfNulls<String>(capacity)

    override var size = 0
        private set

    override val sql: String =
        "INSERT INTO kanji_meta (id, dictionaryId, character, mode, data) VALUES (?, ?, ?, ?, ?)"

    fun add(id: Long, character: String, mode: String, value: String) {
        ids[size] = id
        characters[size] = character
        modes[size] = mode
        data[size] = value
        size++
    }

    override fun bind(row: Int, arguments: MutableList<Any?>) {
        arguments.add(ids[row])
        arguments.add(dictionaryId)
        arguments.add(characters[row])
        arguments.add(modes[row])
        arguments.add(data[row])
    }

    override fun clear() {
        size = 0
    }
}

internal class TagBatch(
    private val capacity: Int,
    private val dictionaryId: Long,
) : ColumnBatch {
    private val ids = LongArray(capacity)
    private val names = arrayOfNulls<String>(capacity)
    private val categories = arrayOfNulls<String>(capacity)
    private val orders = IntArray(capacity)
    private val notes = arrayOfNulls<String>(capacity)
    private val scores = DoubleArray(capacity)

    override var size = 0
        private set

    override val sql: String =
        "INSERT INTO tags (id, dictionaryId, name, category, sortOrder, notes, score) VALUES (?, ?, ?, ?, ?, ?, ?)"

    fun add(id: Long, tag: YomitanTag) {
        ids[size] = id
        names[size] = tag.name
        categories[size] = tag.category
        orders[size] = tag.order
        notes[size] = tag.notes
        scores[size] = tag.score
        size++
    }

    override fun bind(row: Int, arguments: MutableList<Any?>) {
        arguments.add(ids[row])
        arguments.add(dictionaryId)
        arguments.add(names[row])
        arguments.add(categories[row])
        arguments.add(orders[row])
        arguments.add(notes[row])
        arguments.add(scores[row])
    }

    override fun clear() {
        size = 0
    }
}

internal class MediaBatch(
    private val capacity: Int,
    private val dictionaryId: Long,
) : ColumnBatch {
    private val ids = LongArray(capacity)
    private val paths = arrayOfNulls<String>(capacity)
    private val mediaTypes = arrayOfNulls<String>(capacity)
    private val widths = IntArray(capacity)
    private val heights = IntArray(capacity)
    private val data = arrayOfNulls<ByteArray>(capacity)

    override var size = 0
        private set

    override val sql: String =
        "INSERT INTO media (id, dictionaryId, path, mediaType, width, height, data) VALUES (?, ?, ?, ?, ?, ?, ?)"

    fun add(id: Long, path: String, mediaType: String, width: Int?, height: Int?, bytes: ByteArray) {
        ids[size] = id
        paths[size] = path
        mediaTypes[size] = mediaType
        widths[size] = width ?: 0
        heights[size] = height ?: 0
        data[size] = bytes
        size++
    }

    override fun bind(row: Int, arguments: MutableList<Any?>) {
        arguments.add(ids[row])
        arguments.add(dictionaryId)
        arguments.add(paths[row])
        arguments.add(mediaTypes[row])
        val hasSize = widths[row] != 0
        arguments.add(if (hasSize) widths[row] else null)
        arguments.add(if (hasSize) heights[row] else null)
        arguments.add(data[row])
    }

    override fun clear() {
        size = 0
    }
}
