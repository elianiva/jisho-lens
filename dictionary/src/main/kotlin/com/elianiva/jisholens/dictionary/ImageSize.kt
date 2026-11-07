package com.elianiva.jisholens.dictionary

/** Reads intrinsic image dimensions from the file header, without decoding. */
internal object ImageSize {
    fun read(bytes: ByteArray): Pair<Int, Int>? = when {
        isPng(bytes) -> readPng(bytes)
        isGif(bytes) -> readGif(bytes)
        isJpeg(bytes) -> readJpeg(bytes)
        isWebp(bytes) -> readWebp(bytes)
        isBmp(bytes) -> readBmp(bytes)
        else -> null
    }

    private fun isPng(bytes: ByteArray) = bytes.size >= 24 &&
        bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() && bytes[2] == 'N'.code.toByte() &&
        bytes[3] == 'G'.code.toByte()

    private fun readPng(bytes: ByteArray): Pair<Int, Int>? {
        if (bytes.size < 24) return null
        return beInt(bytes, 16) to beInt(bytes, 20)
    }

    private fun isGif(bytes: ByteArray) = bytes.size >= 10 &&
        bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte()

    private fun readGif(bytes: ByteArray): Pair<Int, Int>? {
        if (bytes.size < 10) return null
        return leShort(bytes, 6) to leShort(bytes, 8)
    }

    private fun isJpeg(bytes: ByteArray) = bytes.size >= 3 &&
        bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()

    private fun readJpeg(bytes: ByteArray): Pair<Int, Int>? {
        var offset = 2
        while (offset + 9 < bytes.size) {
            if (bytes[offset] != 0xFF.toByte()) {
                offset++
                continue
            }
            val marker = bytes[offset + 1].toInt() and 0xFF
            val length = (bytes[offset + 2].toInt() and 0xFF shl 8) or (bytes[offset + 3].toInt() and 0xFF)
            val isStartOfFrame = marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC
            if (isStartOfFrame) {
                val height = beShort(bytes, offset + 5)
                val width = beShort(bytes, offset + 7)
                return width to height
            }
            if (length <= 0) return null
            offset += 2 + length
        }
        return null
    }

    private fun isWebp(bytes: ByteArray) = bytes.size >= 30 &&
        bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF" &&
        bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WEBP"

    private fun readWebp(bytes: ByteArray): Pair<Int, Int>? = when (bytes.copyOfRange(12, 16).toString(Charsets.US_ASCII)) {
        "VP8 " -> (leShort(bytes, 26) and 0x3FFF) to (leShort(bytes, 28) and 0x3FFF)
        "VP8L" -> {
            val bits = leInt(bytes, 21)
            ((bits and 0x3FFF) + 1) to (((bits shr 14) and 0x3FFF) + 1)
        }
        "VP8X" -> (leInt24(bytes, 24) + 1) to (leInt24(bytes, 27) + 1)
        else -> null
    }

    private fun isBmp(bytes: ByteArray) = bytes.size >= 26 &&
        bytes[0] == 'B'.code.toByte() && bytes[1] == 'M'.code.toByte()

    private fun readBmp(bytes: ByteArray): Pair<Int, Int>? {
        if (bytes.size < 26) return null
        return leInt(bytes, 18) to leInt(bytes, 22)
    }

    private fun beInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF shl 24) or
            (bytes[offset + 1].toInt() and 0xFF shl 16) or
            (bytes[offset + 2].toInt() and 0xFF shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    private fun beShort(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF shl 8) or (bytes[offset + 1].toInt() and 0xFF)

    private fun leShort(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or (bytes[offset + 1].toInt() and 0xFF shl 8)

    private fun leInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            (bytes[offset + 1].toInt() and 0xFF shl 8) or
            (bytes[offset + 2].toInt() and 0xFF shl 16) or
            (bytes[offset + 3].toInt() and 0xFF shl 24)

    private fun leInt24(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            (bytes[offset + 1].toInt() and 0xFF shl 8) or
            (bytes[offset + 2].toInt() and 0xFF shl 16)
}
