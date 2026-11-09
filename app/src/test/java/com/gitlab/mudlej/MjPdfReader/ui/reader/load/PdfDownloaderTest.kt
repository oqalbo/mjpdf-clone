// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.load

import com.gitlab.mudlej.MjPdfReader.data.OnlineDocumentStore
import com.gitlab.mudlej.MjPdfReader.support.FakeFilesContext
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URL
import java.util.Collections
import javax.net.ssl.SSLException

class PdfDownloaderTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private lateinit var server: HttpServer
    private lateinit var context: FakeFilesContext
    private lateinit var base: String
    private val requests = Collections.synchronizedList(mutableListOf<String>())
    private val progress = mutableListOf<Pair<Long, Long>>()
    private val pdfBytes = ("%PDF-1.7\n" + "x".repeat(20_000) + "\n%%EOF").toByteArray()
    private var storeCalls = 0

    @Before
    fun startServer() {
        context = FakeFilesContext(temporary.newFolder("files"), temporary.newFolder("cache"))
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
        base = "http://127.0.0.1:${server.address.port}"
    }

    @After
    fun stopServer() {
        server.stop(0)
    }

    @Test
    fun fiveRedirectsAreFollowedToThePdf() {
        for (index in 0..4) redirect("/step$index", "/step${index + 1}")
        body("/step5")

        assertStored(downloader().download("$base/step0", false))

        assertEquals((0..5).map { "/step$it" }, requests.toList())
    }

    @Test
    fun sixthRedirectFailsWithoutOpeningItsTarget() {
        for (index in 0..5) redirect("/step$index", "/step${index + 1}")
        body("/step6")

        assertEquals(DownloadResult.HttpError, downloader().download("$base/step0", false))

        assertEquals((0..5).map { "/step$it" }, requests.toList())
        assertEquals(0, storeCalls)
    }

    @Test
    fun redirectWithoutLocationFails() {
        redirect("/missing", null)

        assertEquals(DownloadResult.HttpError, downloader().download("$base/missing", false))
        assertEquals(listOf("/missing"), requests.toList())
        assertEquals(0, storeCalls)
    }

    @Test
    fun relativeRedirectResolvesAgainstTheCurrentDirectory() {
        redirect("/folder/start", "book.pdf")
        body("/folder/book.pdf")

        assertStored(downloader().download("$base/folder/start", false))
        assertEquals(listOf("/folder/start", "/folder/book.pdf"), requests.toList())
    }

    @Test
    fun schemeRelativeRedirectUsesTheCurrentHttpScheme() {
        redirect("/start", "//127.0.0.1:${server.address.port}/book")
        body("/book")

        assertStored(downloader().download("$base/start", false))
        assertEquals(listOf("/start", "/book"), requests.toList())
    }

    @Test
    fun schemeRelativeRedirectKeepsHttpsWhenChangingHosts() {
        val opened = mutableListOf<String>()
        val connections = mutableListOf<FakeConnection>()
        val downloader = downloader(open = { url ->
            opened += url.toString()
            FakeConnection(url, if (opened.size == 1) "//other.example/book" else null).also { connections += it }
        })

        assertStored(downloader.download("https://first.example/start", false))
        assertEquals(listOf("https://first.example/start", "https://other.example/book"), opened)
        assertTrue(connections.all { it.disconnected })
    }

    @Test
    fun secureStartRefusesCleartextRedirectAndReportsItsTarget() {
        val opened = mutableListOf<String>()
        val connection = FakeConnection(URL("https://secure.example/start"), "$base/book")
        val result = downloader(open = { opened += it.toString(); connection })
            .download("https://secure.example/start", false)

        assertEquals("$base/book", (result as DownloadResult.InsecureRedirect).target)
        assertEquals(listOf("https://secure.example/start"), opened)
        assertTrue(connection.disconnected)
        assertEquals(0, storeCalls)
    }

    @Test
    fun explicitPermissionAllowsCleartextRedirectFromSecureStart() {
        body("/book")
        val downloader = downloader(open = { url ->
            if (url.protocol == "https") FakeConnection(url, "$base/book")
            else url.openConnection() as HttpURLConnection
        })

        assertStored(downloader.download("https://secure.example/start", true))
        assertEquals(listOf("/book"), requests.toList())
    }

    @Test
    fun insecureStartDoesNotPromptAfterAnIntermediateSecureRedirect() {
        redirect("/start", "https://secure.example/step")
        body("/book")
        val opened = mutableListOf<String>()
        val downloader = downloader(open = { url ->
            opened += url.toString()
            if (url.protocol == "https") FakeConnection(url, "$base/book")
            else url.openConnection() as HttpURLConnection
        })

        assertStored(downloader.download("$base/start", false))
        assertEquals(listOf("$base/start", "https://secure.example/step", "$base/book"), opened)
    }

    @Test
    fun unsupportedRedirectSchemeFailsBeforeOpeningTheTarget() {
        redirect("/start", "ftp://other.example/book.pdf")

        assertEquals(DownloadResult.HttpError, downloader().download("$base/start", false))
        assertEquals(listOf("/start"), requests.toList())
        assertEquals(0, storeCalls)
    }

    @Test
    fun declaredLengthAboveTheCeilingFailsBeforeWriting() {
        body("/large")

        assertEquals(DownloadResult.TooLarge, downloader(pdfBytes.size.toLong() - 1).download("$base/large", false))
        assertEquals(0, storeCalls)
        assertEquals(emptyList<Pair<Long, Long>>(), progress)
    }

    @Test
    fun zeroAndNegativeCeilingsRefuseEvenAnUnknownLength() {
        body("/chunked", chunked = true)

        for (ceiling in listOf(0L, -1L)) {
            assertEquals(DownloadResult.TooLarge, downloader(ceiling).download("$base/chunked", false))
        }
        assertEquals(0, storeCalls)
    }

    @Test
    fun lengthExactlyAtTheCeilingIsStoredAndReportsKnownProgress() {
        body("/exact")

        assertStored(downloader(pdfBytes.size.toLong()).download("$base/exact", false))
        assertProgress(pdfBytes.size.toLong())
    }

    @Test
    fun unknownLengthIsStoredAndReportsUnknownProgress() {
        body("/chunked", chunked = true)

        assertStored(downloader(pdfBytes.size.toLong()).download("$base/chunked", false))
        assertProgress(-1)
    }

    @Test
    fun unknownLengthOverTheCeilingIsRefusedWhileWritingAndRemovesPartialFiles() {
        body("/chunked", chunked = true)

        assertEquals(DownloadResult.TooLarge, downloader(pdfBytes.size.toLong() - 1).download("$base/chunked", false))
        assertEquals(1, storeCalls)
        assertEquals(emptyList<String>(), context.cacheDir.walkTopDown().filter { it.isFile }.map { it.name }.toList())
    }

    @Test
    fun missingResourceReturnsHttpErrorWithoutWriting() {
        server.createContext("/missing") { exchange ->
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
        }

        assertEquals(DownloadResult.HttpError, downloader().download("$base/missing", false))
        assertEquals(0, storeCalls)
    }

    @Test
    fun connectionSslFailureReturnsSslErrorAndDisconnects() {
        val connection = object : FakeConnection(URL("https://secure.example/book")) {
            override fun connect() { throw SSLException("handshake failed") }
        }

        assertEquals(DownloadResult.SslError, downloader(open = { connection }).download(connection.url.toString(), false))
        assertTrue(connection.disconnected)
        assertEquals(0, storeCalls)
    }

    @Test
    fun nonPdfResponseReturnsNotPdfAndRemovesPartialFiles() {
        body("/html", bytes = "<html>not a PDF</html>".toByteArray())

        assertEquals(DownloadResult.NotPdf, downloader().download("$base/html", false))
        assertEquals(emptyList<String>(), context.cacheDir.walkTopDown().filter { it.isFile }.map { it.name }.toList())
    }

    private fun downloader(
        ceiling: Long = 1024 * 1024,
        open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    ): PdfDownloader = PdfDownloader(
        openConnection = open,
        byteCeiling = { ceiling },
        store = { url, input, limit, onBytes ->
            storeCalls++
            OnlineDocumentStore.write(context, url, false, input(), limit, onBytes)
        },
        onProgress = { bytes, total -> progress += bytes to total },
    )

    private fun redirect(path: String, location: String?) {
        server.createContext(path) { exchange ->
            requests += exchange.requestURI.path
            if (location != null) exchange.responseHeaders.add("Location", location)
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
    }

    private fun body(path: String, chunked: Boolean = false, bytes: ByteArray = pdfBytes) {
        server.createContext(path) { exchange ->
            requests += exchange.requestURI.path
            exchange.sendResponseHeaders(200, if (chunked) 0 else bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
    }

    private fun assertStored(result: DownloadResult) {
        assertArrayEquals(pdfBytes, (result as DownloadResult.Success).file!!.readBytes())
        assertEquals(1, storeCalls)
    }

    private fun assertProgress(total: Long) {
        assertTrue(progress.size >= 3)
        assertEquals(pdfBytes.size.toLong(), progress.last().first)
        assertEquals(setOf(total), progress.map { it.second }.toSet())
        assertTrue(progress.first().first > 0)
        assertTrue(progress.zipWithNext().all { (before, after) -> before.first < after.first })
    }

    private open inner class FakeConnection(url: URL, private val location: String? = null) : HttpURLConnection(url) {
        var disconnected = false
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getResponseCode() = if (location == null) 200 else 302
        override fun getHeaderField(name: String): String? = when (name) {
            "Location" -> location
            "Content-Length" -> pdfBytes.size.toString()
            else -> null
        }
        override fun getInputStream() = ByteArrayInputStream(pdfBytes)
    }
}
