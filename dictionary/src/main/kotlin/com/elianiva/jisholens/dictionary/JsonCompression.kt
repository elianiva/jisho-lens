package com.elianiva.jisholens.dictionary

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

internal object JsonCompression {
    fun inflate(bytes: ByteArray): ByteArray = inflate(bytes, 0, bytes.size)

    fun inflate(bytes: ByteArray, offset: Int, length: Int): ByteArray {
        val inflater = Inflater(false)
        try {
            inflater.setInput(bytes, offset, length)
            val output = ByteArrayOutputStream(maxOf(length * 6, 1024))
            val chunk = ByteArray(1 shl 16)
            while (!inflater.finished()) {
                val read = inflater.inflate(chunk)
                if (read == 0 && inflater.needsInput()) break
                output.write(chunk, 0, read)
            }
            return output.toByteArray()
        } finally {
            inflater.end()
        }
    }

    /**
     * A reusable zlib compressor. A dictionary import compresses blocks of
     * glossary text, and allocating a [Deflater] for each block wastes a native
     * context.
     */
    class Compressor : AutoCloseable {
        private val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, false)
        private var output = ByteArray(1 shl 16)

        fun compress(bytes: ByteArray, offset: Int, length: Int): ByteArray {
            deflater.reset()
            deflater.setInput(bytes, offset, length)
            deflater.finish()
            var written = 0
            while (!deflater.finished()) {
                if (written == output.size) {
                    output = output.copyOf(output.size * 2)
                }
                written += deflater.deflate(output, written, output.size - written)
            }
            return output.copyOf(written)
        }

        override fun close() {
            deflater.end()
        }
    }
}
