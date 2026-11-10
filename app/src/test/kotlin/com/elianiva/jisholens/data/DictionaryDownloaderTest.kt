package com.elianiva.jisholens.data

import com.elianiva.jisholens.dictionary.ImportCancelledException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import kotlin.concurrent.thread

class DictionaryDownloaderTest {
    private lateinit var directory: File
    private val servers = mutableListOf<TestHttpServer>()

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("download").toFile()
    }

    @After
    fun tearDown() {
        servers.forEach { it.close() }
        directory.deleteRecursively()
    }

    private fun serve(status: Int, body: ByteArray): String {
        val server = TestHttpServer(status, body)
        servers.add(server)
        return "http://127.0.0.1:${server.port}/dictionary.zip"
    }

    @Test
    fun `downloads the archive and reports progress`() = runBlocking {
        val bytes = ByteArray(700_000) { (it % 251).toByte() }
        val target = File(directory, "dictionary.zip")
        val reported = mutableListOf<Pair<Long, Long?>>()

        val file = DictionaryDownloader.download(
            url = serve(200, bytes),
            target = target,
            onProgress = { copied, total -> reported += copied to total },
        )

        assertArrayEquals(bytes, file.readBytes())
        assertEquals(bytes.size.toLong(), reported.last().first)
        assertEquals(bytes.size.toLong(), reported.last().second)
        assertTrue(reported.first().first < reported.last().first)
        assertFalse(File(directory, "dictionary.zip.part").exists())
    }

    @Test
    fun `a server error leaves no file behind`() {
        val target = File(directory, "missing.zip")

        assertThrows(IOException::class.java) {
            runBlocking { DictionaryDownloader.download(serve(404, ByteArray(0)), target) }
        }
        assertFalse(target.exists())
        assertFalse(File(directory, "missing.zip.part").exists())
    }

    @Test
    fun `cancelling stops the download and removes the partial file`() {
        val target = File(directory, "big.zip")

        assertThrows(ImportCancelledException::class.java) {
            runBlocking {
                DictionaryDownloader.download(
                    url = serve(200, ByteArray(2_000_000) { 1 }),
                    target = target,
                    shouldContinue = { false },
                )
            }
        }
        assertFalse(target.exists())
        assertFalse(File(directory, "big.zip.part").exists())
    }

    @Test
    fun `the catalogue points at the publisher and says what it is`() {
        assertTrue(DictionaryCatalog.sources.size >= 2)
        for (source in DictionaryCatalog.sources) {
            assertTrue(source.url.startsWith("https://"))
            assertTrue(source.url.endsWith(".zip"))
            assertTrue(source.pageUrl.startsWith("https://"))
            assertTrue(source.sizeMegabytes > 0)
            assertEquals("CC BY-SA 4.0", source.license)
            assertTrue(source.description.isNotBlank())
        }
    }
}

/**
 * The smallest HTTP/1.1 server that answers one request at a time. The JDK's own
 * `com.sun.net.httpserver` is not on the Android classpath, and a real server library
 * is not worth a dependency for four tests.
 */
private class TestHttpServer(private val status: Int, private val body: ByteArray) : Closeable {
    private val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
    private val worker = thread(isDaemon = true, name = "test-http") {
        while (!server.isClosed) {
            try {
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                    }
                    val head = if (status == 200) {
                        "HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                    } else {
                        "HTTP/1.1 $status Error\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                    }
                    val output = socket.getOutputStream()
                    output.write(head.toByteArray())
                    if (status == 200) output.write(body)
                    output.flush()
                }
            } catch (_: IOException) {
                return@thread
            }
        }
    }

    val port: Int get() = server.localPort

    override fun close() {
        server.close()
    }
}
