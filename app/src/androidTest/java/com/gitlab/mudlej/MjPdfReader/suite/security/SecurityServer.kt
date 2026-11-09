// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.security

import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.Buffer
import org.junit.rules.ExternalResource
import java.io.Closeable
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory

class SecurityServer : ExternalResource(), Closeable {
    private var primary: MockWebServer? = null
    private var tls: MockWebServer? = null
    private var streaming: ServerSocket? = null
    private var streamingThread: Thread? = null

    private val primaryRoutes = LinkedHashMap<String, MockResponse>()
    private val tlsRoutes = LinkedHashMap<String, MockResponse>()

    private val primaryLog = mutableListOf<String>()
    private val tlsLog = mutableListOf<String>()

    private var previousSocketFactory: SSLSocketFactory? = null
    private var trustInstalled = false

    override fun before() {
        primary = start(primaryRoutes, primaryLog)
    }

    override fun after() {
        close()
    }

    override fun close() {
        runCatching { streaming?.close() }
        runCatching { streamingThread?.join(STREAMING_JOIN_TIMEOUT_MS) }
        restoreDefaultTrust()
        runCatching { primary?.shutdown() }
        runCatching { tls?.shutdown() }
        primary = null
        tls = null
    }

    val port: Int get() = requirePrimary().port

    fun urlFor(path: String): String = "http://$LOOPBACK:$port$path"

    fun requestPaths(): List<String> = synchronized(primaryLog) { primaryLog.toList() }

    fun clearRequestLog() {
        synchronized(primaryLog) { primaryLog.clear() }
    }

    fun assertRequestLogEmpty(what: String) {
        val paths = requestPaths()
        if (paths.isNotEmpty()) {
            throw AssertionError("$what reached the local server: $paths")
        }
    }

    fun holdRequestLogEmpty(what: String, windowMs: Long = SecurityReader.INERT_WINDOW_MS) {
        Await.assertThroughout(
            condition = "$what leaves the local server request log empty",
            windowMs = windowMs,
        ) { requestPaths().isEmpty() }
    }

    fun awaitRequestCount(expected: Int, timeoutMs: Long = Await.LONG_TIMEOUT_MS): List<String> =
        Await.untilValue(
            "the local server records $expected request(s)",
            timeoutMs,
            pollMs = 200L,
            read = { requestPaths() },
        ) { it.size >= expected }

    fun servePdf(path: String, file: File): String = serveBytes(path, file.readBytes(), PDF_MIME_TYPE)

    fun serveBytes(path: String, bytes: ByteArray, contentType: String = PDF_MIME_TYPE): String {
        primaryRoutes[path] = MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", contentType)
            .setBody(Buffer().write(bytes))
        return urlFor(path)
    }

    fun serveRedirectLoop(path: String): String {
        primaryRoutes[path] = MockResponse()
            .setResponseCode(302)
            .setHeader("Location", urlFor(path))
        return urlFor(path)
    }

    fun serveThrottledOversizedBody(path: String, totalBytes: Long, bytesPerPeriod: Long, periodMs: Long): String {
        check(streaming == null) { "Only one streaming response may be active per test" }
        val server = ServerSocket(0, 1, InetAddress.getByName(LOOPBACK))
        streaming = server
        streamingThread = thread(name = "security-streaming-response") {
            runCatching {
                server.accept().use { socket ->
                    val input = socket.getInputStream().bufferedReader()
                    val requestLine = input.readLine().orEmpty()
                    while (!input.readLine().isNullOrEmpty()) Unit
                    synchronized(primaryLog) {
                        primaryLog.add(requestLine.substringAfter(' ').substringBefore(' '))
                    }

                    val output = socket.getOutputStream().buffered()
                    output.write(
                        ("HTTP/1.1 200 OK\r\n" +
                            "Content-Type: $PDF_MIME_TYPE\r\n" +
                            "Content-Length: $totalBytes\r\n" +
                            "Connection: close\r\n\r\n").toByteArray()
                    )
                    output.flush()

                    val chunk = ByteArray(OVERSIZED_CHUNK_BYTES)
                    var written = 0L
                    var periodBytes = 0L
                    while (written < totalBytes && !socket.isClosed) {
                        val count = minOf(chunk.size.toLong(), totalBytes - written).toInt()
                        output.write(chunk, 0, count)
                        written += count
                        periodBytes += count
                        if (periodBytes >= bytesPerPeriod) {
                            output.flush()
                            Thread.sleep(periodMs)
                            periodBytes = 0L
                        }
                    }
                    output.flush()
                }
            }
        }
        return "http://$LOOPBACK:${server.localPort}$path"
    }

    fun serveStalledBody(path: String, declaredLength: Long): String {
        primaryRoutes[path] = MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", PDF_MIME_TYPE)
            .setHeader("Content-Length", declaredLength.toString())
            .setBody(Buffer().write(ByteArray(STALL_PREFIX_BYTES)))
            .setSocketPolicy(SocketPolicy.NO_RESPONSE)
        return urlFor(path)
    }

    fun startHttps(trusted: Boolean): Int {
        tls?.let { return it.port }

        val authority = HeldCertificate.Builder()
            .certificateAuthority(0)
            .commonName(AUTHORITY_NAME)
            .build()
        val serverCertificate = HeldCertificate.Builder()
            .commonName(LOOPBACK)
            .addSubjectAlternativeName(LOOPBACK)
            .signedBy(authority)
            .build()
        val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(serverCertificate, authority.certificate)
            .build()

        val started = MockWebServer()
        started.useHttps(serverCertificates.sslSocketFactory(), false)
        started.dispatcher = dispatcherOver(tlsRoutes, tlsLog)
        started.start(InetAddress.getByName(LOOPBACK), 0)
        tls = started

        if (trusted) {
            installTrust(authority)
        }
        return started.port
    }

    fun httpsUrlFor(path: String): String {
        val running = tls ?: throw AssertionError("No https listener is running")
        return "https://$LOOPBACK:${running.port}$path"
    }

    fun serveOverHttps(path: String, bytes: ByteArray, contentType: String = PDF_MIME_TYPE): String {
        tlsRoutes[path] = MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", contentType)
            .setBody(Buffer().write(bytes))
        return httpsUrlFor(path)
    }

    fun serveHttpsRedirectTo(path: String, location: String): String {
        tlsRoutes[path] = MockResponse()
            .setResponseCode(302)
            .setHeader("Location", location)
        return httpsUrlFor(path)
    }

    fun httpsRequestPaths(): List<String> = synchronized(tlsLog) { tlsLog.toList() }

    private fun installTrust(authority: HeldCertificate) {
        if (trustInstalled) return
        previousSocketFactory = HttpsURLConnection.getDefaultSSLSocketFactory()
        val clientCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(authority.certificate)
            .addPlatformTrustedCertificates()
            .build()
        HttpsURLConnection.setDefaultSSLSocketFactory(clientCertificates.sslSocketFactory())
        trustInstalled = true
    }

    private fun restoreDefaultTrust() {
        if (!trustInstalled) return
        previousSocketFactory?.let { HttpsURLConnection.setDefaultSSLSocketFactory(it) }
        previousSocketFactory = null
        trustInstalled = false
    }

    private fun start(routes: Map<String, MockResponse>, log: MutableList<String>): MockWebServer {
        val server = MockWebServer()
        server.dispatcher = dispatcherOver(routes, log)
        server.start(InetAddress.getByName(LOOPBACK), 0)
        return server
    }

    private fun dispatcherOver(routes: Map<String, MockResponse>, log: MutableList<String>): Dispatcher =
        object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                synchronized(log) { log.add(path) }
                return routes[path.substringBefore('?')] ?: routes[path] ?: MockResponse().setResponseCode(404)
            }
        }

    private fun requirePrimary(): MockWebServer =
        primary ?: throw AssertionError("The security server is not running")

    companion object {
        const val LOOPBACK = "127.0.0.1"
        const val PDF_MIME_TYPE = "application/pdf"
        const val HTML_MIME_TYPE = "text/html"
        const val OVERSIZED_CHUNK_BYTES = 64 * 1024
        const val STALL_PREFIX_BYTES = 1024
        const val STREAMING_JOIN_TIMEOUT_MS = 2_000L
        const val AUTHORITY_NAME = "MJ PDF suite authority"
    }
}
