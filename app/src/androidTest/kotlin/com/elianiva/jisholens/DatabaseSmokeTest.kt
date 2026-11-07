package com.elianiva.jisholens

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.elianiva.jisholens.data.DictionaryDatabase
import com.elianiva.jisholens.data.DictionaryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class DatabaseSmokeTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun importsAndLooksUp() = runBlocking {
        val database = DictionaryDatabase(context)
        val repository = DictionaryRepository(database)
        val archive = File(context.cacheDir, "smoke-dictionary.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("index.json"))
            zip.write("""{"title":"Smoke Test","format":3,"revision":"1"}""".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("term_bank_1.json"))
            zip.write(
                """[["食べる","たべる","v1","v1",10,[{"type":"structured-content","content":[{"tag":"span","content":"to eat"}]}],1,""]]"""
                    .toByteArray(),
            )
            zip.closeEntry()
        }

        val summary = repository.import(archive)
        assertEquals("Smoke Test", summary.title)
        assertEquals(1, summary.counts["terms"])

        val entries = repository.lookup("食べる")
        assertEquals(1, entries.size)
        assertEquals("食べる", entries.single().expression)
        assertTrue(entries.single().tags.any { it.name == "v1" })
    }
}
