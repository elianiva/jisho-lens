package com.elianiva.jisholens.dictionary

/**
 * Appends glossaries to large compressed blocks instead of compressing each row.
 *
 * Per-row zlib spends about 40 bytes on a stream header for a few hundred bytes
 * of text, so a 500,000-term dictionary pays several megabytes of headers and
 * thousands of small compressions. Compressing 4 MiB blocks cuts the compressed
 * size by an order of magnitude and makes compression several times faster.
 *
 * Each term stores the block id, the byte offset, and the length inside the
 * uncompressed block.
 */
internal class GlossaryBlockWriter(
    private val database: SqliteDatabase,
    private val dictionaryId: Long,
    private val targetSize: Int = 4 * 1024 * 1024,
) : AutoCloseable {
    private val compressor = JsonCompression.Compressor()
    private var buffer = ByteArray(targetSize)
    private var length = 0
    private var currentBlockId = nextBlockId()

    var blockId = 0L
        private set

    var offset = 0
        private set

    var size = 0
        private set

    fun add(value: String) {
        add(value.toByteArray(Charsets.UTF_8))
    }

    /** Encodes a range of the bank text straight into the block, with no intermediate string. */
    fun add(text: String, start: Int, end: Int) {
        val worstCase = Utf8.maxBytes(end - start)
        if (length + worstCase > buffer.size) {
            if (length > 0) flush()
            if (worstCase > buffer.size) buffer = buffer.copyOf(maxOf(worstCase, buffer.size * 2))
        }
        blockId = currentBlockId
        offset = length
        val written = Utf8.encode(text, start, end, buffer, length)
        size = written
        length += written
    }

    fun add(bytes: ByteArray) {
        if (length > 0 && length + bytes.size > buffer.size) {
            flush()
        }
        if (length + bytes.size > buffer.size) {
            buffer = buffer.copyOf(maxOf(bytes.size, buffer.size * 2))
        }
        System.arraycopy(bytes, 0, buffer, length, bytes.size)
        blockId = currentBlockId
        offset = length
        size = bytes.size
        length += bytes.size
    }

    fun flush() {
        if (length == 0) return
        val compressed = compressor.compress(buffer, 0, length)
        database.execute(
            "INSERT INTO glossary_blocks (id, dictionaryId, data) VALUES (?, ?, ?)",
            listOf(currentBlockId, dictionaryId, compressed),
        )
        currentBlockId++
        length = 0
    }

    override fun close() {
        flush()
        compressor.close()
    }

    private fun nextBlockId(): Long =
        (database.query("SELECT COALESCE(MAX(id), 0) + 1 FROM glossary_blocks").first().first() as Number).toLong()
}
