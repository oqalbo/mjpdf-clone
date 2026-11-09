// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.load

import android.util.Log
import com.gitlab.mudlej.MjPdfReader.core.io.urlForLog
import com.gitlab.mudlej.MjPdfReader.core.net.contentLengthCompat
import com.gitlab.mudlej.MjPdfReader.data.OnlineDocumentStore
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.SSLException

internal sealed class DownloadResult {
    class Success(val file: File?) : DownloadResult()
    class InsecureRedirect(val target: String) : DownloadResult()
    data object TooLarge : DownloadResult()
    data object NotPdf : DownloadResult()
    data object HttpError : DownloadResult()
    data object SslError : DownloadResult()
    data object GenericError : DownloadResult()
}

internal class PdfDownloader(
    private val openConnection: (URL) -> HttpURLConnection,
    private val byteCeiling: () -> Long,
    private val store: (String, () -> InputStream, Long, (Long) -> Unit) -> OnlineDocumentStore.WriteResult,
    private val onProgress: (Long, Long) -> Unit,
    private val onContentLength: (Long) -> Unit = {},
) {
    fun download(url: String, allowInsecureRedirect: Boolean): DownloadResult {
        var connection: HttpURLConnection? = null
        try {
            var currentUrl = URL(url)
            val startedSecure = currentUrl.protocol.equals("https", ignoreCase = true)
            var redirects = 0
            while (true) {
                val conn = openConnection(currentUrl).apply {
                    requestMethod = "GET"
                    instanceFollowRedirects = false
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    setRequestProperty("User-Agent", USER_AGENT)
                }
                connection = conn
                conn.connect()
                when (val responseCode = conn.responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        val contentLength = conn.contentLengthCompat()
                        val ceiling = byteCeiling()
                        if (ceiling <= 0L || contentLength > ceiling) {
                            Log.e(TAG, "Refusing a download of $contentLength bytes against a ceiling of $ceiling")
                            return DownloadResult.TooLarge
                        }
                        onContentLength(contentLength)
                        val written = store(url, { conn.inputStream }, ceiling) { totalBytes ->
                            onProgress(totalBytes, contentLength)
                        }
                        return when (written) {
                            is OnlineDocumentStore.WriteResult.Stored -> DownloadResult.Success(written.file)
                            is OnlineDocumentStore.WriteResult.TooLarge -> DownloadResult.TooLarge
                            is OnlineDocumentStore.WriteResult.NotPdf -> DownloadResult.NotPdf
                            is OnlineDocumentStore.WriteResult.Failed -> DownloadResult.GenericError
                        }
                    }
                    HttpURLConnection.HTTP_MOVED_PERM,
                    HttpURLConnection.HTTP_MOVED_TEMP,
                    HttpURLConnection.HTTP_SEE_OTHER,
                    HTTP_TEMPORARY_REDIRECT,
                    HTTP_PERMANENT_REDIRECT -> {
                        val location = conn.getHeaderField("Location")
                        if (location == null || redirects >= MAX_REDIRECTS) {
                            Log.e(TAG, "Redirect could not be followed for URL : ${url.urlForLog()}")
                            return DownloadResult.HttpError
                        }
                        val target = runCatching { URL(currentUrl, location) }.getOrNull()
                        if (target == null || !target.protocol.isOnlinePdfScheme()) {
                            Log.e(TAG, "Redirect target is not an http or https address for URL : ${url.urlForLog()}")
                            return DownloadResult.HttpError
                        }
                        val downgrades = startedSecure && target.protocol.equals("http", ignoreCase = true)
                        if (downgrades && !allowInsecureRedirect) {
                            Log.e(TAG, "Redirect downgrades to cleartext for URL : ${url.urlForLog()}")
                            return DownloadResult.InsecureRedirect(target.toString())
                        }
                        redirects++
                        currentUrl = target
                        conn.disconnect()
                    }
                    else -> {
                        Log.e(TAG, "Error during http request, response code : $responseCode")
                        return DownloadResult.HttpError
                    }
                }
            }
        } catch (e: SSLException) {
            Log.e(TAG, "Error cannot get file at URL : ${url.urlForLog()}", e)
            return DownloadResult.SslError
        } catch (e: IOException) {
            Log.e(TAG, "Error cannot get file at URL : ${url.urlForLog()}", e)
            return DownloadResult.GenericError
        } catch (e: Exception) {
            Log.e(TAG, "Error cannot get file at URL : ${url.urlForLog()}", e)
            return DownloadResult.GenericError
        } finally {
            connection?.disconnect()
        }
    }

    private companion object {
        const val TAG = "OnlinePdfController"
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
        const val CONNECT_TIMEOUT_MS = 20_000
        const val READ_TIMEOUT_MS = 30_000
        const val MAX_REDIRECTS = 5
        const val HTTP_TEMPORARY_REDIRECT = 307
        const val HTTP_PERMANENT_REDIRECT = 308
    }
}
