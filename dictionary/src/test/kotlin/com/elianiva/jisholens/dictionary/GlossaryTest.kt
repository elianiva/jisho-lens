package com.elianiva.jisholens.dictionary

import kotlin.test.Test
import kotlin.test.assertEquals

class GlossaryTest {
    @Test
    fun `joins top level entries with semicolons`() {
        assertEquals("to eat; to live on", Glossary.text("""["to eat","to live on"]"""))
    }

    @Test
    fun `reads structured content and skips markup keys`() {
        val json = """
            [{"type":"structured-content","content":{"tag":"ul","content":[
                {"tag":"li","content":["to eat","to consume"]}
            ]}}]
        """.trimIndent()
        assertEquals("to eat to consume", Glossary.text(json))
    }

    @Test
    fun `separates block elements`() {
        val json = """
            [{"type":"structured-content","content":{"tag":"ul","content":[
                {"tag":"li","content":["to eat"]},
                {"tag":"li","content":["to consume"]}
            ]}}]
        """.trimIndent()
        assertEquals("to eat; to consume", Glossary.text(json))
    }

    @Test
    fun `skips style values`() {
        val json = """[{"type":"structured-content","content":{"tag":"span","style":{"fontStyle":"italic"},"content":["振り仮名"]}}]"""
        assertEquals("振り仮名", Glossary.text(json))
    }

    @Test
    fun `keeps image alt text`() {
        val json = """[{"type":"image","path":"img/picture.png","width":100,"alt":"a picture"}]"""
        assertEquals("a picture", Glossary.text(json))
    }

    @Test
    fun `strips html tags`() {
        assertEquals("eat food", Glossary.text("""["<i>eat</i> food"]"""))
    }

    @Test
    fun `decodes json escapes`() {
        assertEquals("line one line two", Glossary.text("""["line one\nline two"]"""))
        assertEquals("食", Glossary.text("""["\u98df"]"""))
    }

    @Test
    fun `handles empty glossaries`() {
        assertEquals("", Glossary.text("[]"))
        assertEquals("", Glossary.text("""[""]"""))
    }
}

class ImageSizeTest {
    @Test
    fun `reads png dimensions`() {
        assertEquals(320 to 200, ImageSize.read(Fixtures.png(320, 200)))
    }

    @Test
    fun `reads gif dimensions`() {
        assertEquals(64 to 32, ImageSize.read(Fixtures.gif(64, 32)))
    }

    @Test
    fun `returns null for unknown formats`() {
        assertEquals(null, ImageSize.read("<svg></svg>".toByteArray()))
    }
}
