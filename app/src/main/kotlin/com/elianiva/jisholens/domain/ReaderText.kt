package com.elianiva.jisholens.domain

import com.elianiva.jisholens.dictionary.FtsText

/** The three file formats the reader accepts. */
enum class ReaderFormat(val label: String, val extension: String) {
    TEXT("Plain text", "txt"),
    SUBRIP("SubRip subtitles", "srt"),
    WEBVTT("WebVTT subtitles", "vtt"),
}

/** The format of [fileName], or null when the reader does not open it. */
fun readerFormat(fileName: String): ReaderFormat? {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return ReaderFormat.entries.firstOrNull { it.extension == extension }
}

/**
 * Splits an imported file into the lines the reader shows.
 *
 * Plain text keeps its own line breaks, because a poem, a lyric, and a paragraph
 * all break where the author meant them to. Subtitles lose their cue numbers,
 * timings, and markup: one cue is one line, and a cue that wraps over several
 * source lines becomes one line so a tap cannot fall between its halves.
 */
fun readerLines(content: String, format: ReaderFormat): List<String> {
    val text = content.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
    return when (format) {
        ReaderFormat.TEXT -> text.lines().map(String::trim).filter(String::isNotEmpty)
        ReaderFormat.SUBRIP -> subtitleLines(text, webVtt = false)
        ReaderFormat.WEBVTT -> subtitleLines(text, webVtt = true)
    }
}

/** Decodes a picked file, honouring the UTF-16 byte-order mark that subtitle exports use. */
fun decodeReaderText(bytes: ByteArray): String = when {
    bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
        String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
    bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
        String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
    else -> String(bytes, Charsets.UTF_8)
}

private fun subtitleLines(text: String, webVtt: Boolean): List<String> {
    val lines = ArrayList<String>()
    val cue = StringBuilder()
    // A WebVTT file opens with a header block that ends at its first blank line.
    var inHeader = webVtt
    var inNote = false

    fun flush() {
        val value = cue.toString().trim()
        if (value.isNotEmpty()) lines += value
        cue.clear()
    }

    for (raw in text.lines()) {
        val line = raw.trim()
        if (line.isEmpty()) {
            flush()
            inHeader = false
            inNote = false
            continue
        }
        if (inHeader) {
            if (line.startsWith("WEBVTT")) inHeader = true
            continue
        }
        if (webVtt && (line == "NOTE" || line.startsWith("NOTE "))) {
            inNote = true
            continue
        }
        if (inNote) continue
        when {
            line.contains("-->") -> cue.clear()
            !webVtt && isCueNumber(line) -> Unit
            else -> {
                val cleaned = cleanSubtitle(line)
                if (cleaned.isNotEmpty()) {
                    if (cue.isNotEmpty() && needsSpace(cue.last(), cleaned.first())) cue.append(' ')
                    cue.append(cleaned)
                }
            }
        }
    }
    flush()
    return lines
}

/** A line break inside a subtitle cue is a word break in Latin text, but not in Japanese. */
private fun needsSpace(previous: Char, next: Char): Boolean =
    !FtsText.isJapanese(previous.code) && !FtsText.isJapanese(next.code)

/** A SubRip cue index: digits alone on a line. */
private fun isCueNumber(line: String): Boolean = line.isNotEmpty() && line.all(Char::isDigit)

/** Removes the inline markup that subtitle files use for italics, positioning, and styling. */
private fun cleanSubtitle(line: String): String =
    line.replace(Regex("\\{[^}]*}"), "")
        .replace(Regex("</?[A-Za-z][^>]*>"), "")
        .replace(Regex("<[^>]*>"), "")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
        .trim()
