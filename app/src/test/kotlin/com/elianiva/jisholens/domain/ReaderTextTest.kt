package com.elianiva.jisholens.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderTextTest {
    @Test
    fun `recognises only the three formats`() {
        assertEquals(ReaderFormat.TEXT, readerFormat("story.TXT"))
        assertEquals(ReaderFormat.SUBRIP, readerFormat("episode.srt"))
        assertEquals(ReaderFormat.WEBVTT, readerFormat("episode.VTT"))
        assertNull(readerFormat("book.epub"))
        assertNull(readerFormat("README"))
    }

    @Test
    fun `plain text keeps its own non-empty lines`() {
        val lines = readerLines("  一行目  \r\n\r\n二行目\n\n  三行目", ReaderFormat.TEXT)
        assertEquals(listOf("一行目", "二行目", "三行目"), lines)
    }

    @Test
    fun `subrip drops indices timings and markup and joins a wrapped cue`() {
        val srt = """
            1
            00:00:01,000 --> 00:00:02,000
            <i>こんにちは</i>
            世界

            2
            00:00:03,000 --> 00:00:04,000
            {\an8}さようなら
        """.trimIndent()
        assertEquals(listOf("こんにちは世界", "さようなら"), readerLines(srt, ReaderFormat.SUBRIP))
    }

    @Test
    fun `webvtt skips the header, notes, and cue identifiers`() {
        val vtt = """
            WEBVTT
            Kind: captions
            Language: ja

            NOTE a comment
            that continues here

            cue-1
            00:00:01.000 --> 00:00:02.000 align:start
            <v Speaker>おはよう</v>

            00:00:03.000 --> 00:00:04.000
            こんばんは
        """.trimIndent()
        assertEquals(listOf("おはよう", "こんばんは"), readerLines(vtt, ReaderFormat.WEBVTT))
    }

    @Test
    fun `decodes a UTF-16 subtitle`() {
        val text = "こんにちは"
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)
        assertEquals(text, decodeReaderText(bytes))
    }
}
