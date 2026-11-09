// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.net

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.rules.ExternalResource
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class LocalPdfServer : ExternalResource(), Closeable {

    private val server = MockWebServer()
    private val routes = LinkedHashMap<String, MockResponse>()
    private val requestedPaths = CopyOnWriteArrayList<String>()
    private var started = false

    val requestCount: Int get() = server.requestCount

    override fun before() {
        start()
    }

    override fun after() {
        shutdown()
    }

    override fun close() {
        shutdown()
    }

    fun start() {
        if (started) return
        server.start(InetAddress.getByName(LOOPBACK), 0)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                requestedPaths += path
                return routes[path] ?: routes[path.substringBefore('?')] ?: MockResponse().setResponseCode(404)
            }
        }
        started = true
    }

    fun shutdown() {
        if (!started) return
        runCatching { server.shutdown() }
        started = false
    }

    val port: Int get() = server.port

    fun urlFor(path: String): String = "http://$LOOPBACK:${server.port}$path"

    fun wasRequested(path: String): Boolean = requestedPaths.any { it.substringBefore('?') == path }

    fun takeRequest(timeoutMs: Long = 5_000L): RecordedRequest? =
        server.takeRequest(timeoutMs, TimeUnit.MILLISECONDS)

    fun drainRequestPaths(): List<String> {
        val paths = mutableListOf<String>()
        while (true) {
            val request = takeRequest(50L) ?: break
            paths += request.path.orEmpty()
        }
        return paths
    }

    fun servePdfBytes(path: String, bytes: ByteArray): String {
        routes[path] = MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", PDF_MIME_TYPE)
            .setBody(Buffer().write(bytes))
        return urlFor(path)
    }

    fun serveThrottledPdfBytes(
        path: String,
        bytes: ByteArray,
        bytesPerPeriod: Long,
        periodMs: Long,
    ): String {
        routes[path] = MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", PDF_MIME_TYPE)
            .setBody(Buffer().write(bytes))
            .throttleBody(bytesPerPeriod, periodMs, TimeUnit.MILLISECONDS)
        return urlFor(path)
    }

    fun serveNotFound(path: String): String {
        routes[path] = MockResponse().setResponseCode(404)
        return urlFor(path)
    }

    fun serveRedirect(path: String, toPath: String, code: Int = 302): String =
        serveRedirectTo(path, urlFor(toPath), code)

    fun serveRedirectTo(path: String, location: String, code: Int = 302): String {
        routes[path] = MockResponse()
            .setResponseCode(code)
            .setHeader("Location", location)
        return urlFor(path)
    }

    fun serveRedirectLoop(path: String): String = serveRedirectTo(path, urlFor(path))

    fun serveDisconnect(path: String, policy: SocketPolicy = SocketPolicy.DISCONNECT_AT_START): String {
        routes[path] = MockResponse().apply { socketPolicy = policy }
        return urlFor(path)
    }

    companion object {
        const val LOOPBACK = "127.0.0.1"
        const val PDF_MIME_TYPE = "application/pdf"

        fun closedPortUrl(path: String): String {
            val port = ServerSocket(0).use { it.localPort }
            return "http://$LOOPBACK:$port$path"
        }
    }
}
