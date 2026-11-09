// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.scan

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Process
import android.os.SystemClock
import com.gitlab.mudlej.MjPdfReader.data.entity.SourceFingerprint
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.ui.home.metadata.IPdfMetadataCallback
import com.gitlab.mudlej.MjPdfReader.ui.home.metadata.IPdfMetadataService
import com.gitlab.mudlej.MjPdfReader.ui.home.metadata.MetadataProtocol
import com.gitlab.mudlej.MjPdfReader.ui.home.metadata.PdfMetadataService
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue

internal data class MetadataWorkerIdentity(val pid: Int, val instance: Long)

internal data class MetadataWorkerResult(
    val generation: Long,
    val connectionToken: Long,
    val fingerprint: SourceFingerprint,
    val hash: String?,
    val pageCount: Int,
    val failure: String?,
    val worker: MetadataWorkerIdentity,
)

internal class MetadataWorkerBinding : IPdfMetadataCallback.Stub(), ServiceConnection, AutoCloseable {
    private val context = TestEnvironment.targetContext
    private val connected = LinkedBlockingQueue<IPdfMetadataService>()
    private val ready = LinkedBlockingQueue<MetadataWorkerIdentity>()
    private val acknowledgements = LinkedBlockingQueue<Pair<Long, Long>>()
    val results = LinkedBlockingQueue<MetadataWorkerResult>()
    val deathAt = AtomicLong()
    private var bound = false
    private var service: IPdfMetadataService? = null
    private val token = SystemClock.elapsedRealtimeNanos()

    fun connect(): MetadataWorkerIdentity {
        bound = context.bindService(Intent(context, PdfMetadataService::class.java), this, Context.BIND_AUTO_CREATE)
        assertTrue("The metadata service accepts the binding", bound)
        val remote = connected.poll(Await.LONG_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            ?: throw AssertionError("The metadata service never connected")
        service = remote
        remote.asBinder().linkToDeath({ deathAt.compareAndSet(0L, SystemClock.elapsedRealtime()) }, 0)
        remote.registerCallback(this, GENERATION, token)
        val worker = ready.poll(Await.LONG_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            ?: throw AssertionError("The metadata worker never became ready")
        assertNotEquals("The metadata service is out of process", Process.myPid(), worker.pid)
        return worker
    }

    fun process(file: File, debugBehavior: Int = MetadataProtocol.DEBUG_NONE) {
        val fingerprint = fingerprint(file)
        checkNotNull(service).process(
            GENERATION, token, fingerprint.path, fingerprint.size, fingerprint.lastModified, debugBehavior,
        )
    }

    fun awaitHealthyResult(file: File, pageCount: Int, worker: MetadataWorkerIdentity) {
        val result = results.poll(Await.LONG_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            ?: throw AssertionError("No metadata result for ${file.name}")
        assertEquals(GENERATION, result.generation)
        assertEquals(token, result.connectionToken)
        assertEquals(fingerprint(file), result.fingerprint)
        assertEquals(MetadataProtocol.FAILURE_NONE, result.failure)
        assertEquals(pageCount, result.pageCount)
        assertTrue("Successful metadata includes the document hash", !result.hash.isNullOrEmpty())
        assertEquals("The same worker handles the healthy request", worker, result.worker)
    }

    fun acknowledge(file: File) {
        val fingerprint = fingerprint(file)
        checkNotNull(service).acknowledge(
            GENERATION, token, fingerprint.path, fingerprint.size, fingerprint.lastModified, MetadataProtocol.ACK_CONTINUE,
        )
        assertEquals(
            "The worker accepts ACK_CONTINUE",
            GENERATION to token,
            acknowledgements.poll(Await.LONG_TIMEOUT_MS, TimeUnit.MILLISECONDS),
        )
    }

    fun isAlive(): Boolean = service?.asBinder()?.pingBinder() == true

    private fun fingerprint(file: File): SourceFingerprint =
        SourceFingerprint.normalized(file.absolutePath, file.length(), file.lastModified())

    fun unbind() {
        if (bound) {
            context.unbindService(this)
            bound = false
        }
    }

    override fun close() {
        unbind()
        if (service != null) {
            Await.until("the unbound metadata worker exits", Await.LONG_TIMEOUT_MS) { !isAlive() }
        }
    }

    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
        connected.offer(IPdfMetadataService.Stub.asInterface(binder))
    }

    override fun onServiceDisconnected(name: ComponentName) = Unit

    override fun onReady(
        generation: Long,
        connectionToken: Long,
        workerPid: Int,
        workerInstance: Long,
        baselineNativePssBytes: Long,
        nativePssBytes: Long,
        availableBytes: Long,
        thresholdBytes: Long,
        totalBytes: Long,
        lowMemory: Boolean,
    ) {
        ready.offer(MetadataWorkerIdentity(workerPid, workerInstance))
    }

    override fun onMemorySample(
        generation: Long,
        connectionToken: Long,
        workerPid: Int,
        workerInstance: Long,
        baselineNativePssBytes: Long,
        nativePssBytes: Long,
        availableBytes: Long,
        thresholdBytes: Long,
        totalBytes: Long,
        lowMemory: Boolean,
    ) = Unit

    override fun onResult(
        generation: Long,
        connectionToken: Long,
        path: String,
        size: Long,
        lastModified: Long,
        hash: String?,
        pageCount: Int,
        failureCode: String?,
        workerPid: Int,
        workerInstance: Long,
        baselineNativePssBytes: Long,
        maxActiveDocuments: Int,
        nativePssBytes: Long,
        availableBytes: Long,
        thresholdBytes: Long,
        totalBytes: Long,
        lowMemory: Boolean,
    ) {
        results.offer(
            MetadataWorkerResult(
                generation, connectionToken, SourceFingerprint(path, size, lastModified),
                hash, pageCount, failureCode, MetadataWorkerIdentity(workerPid, workerInstance),
            )
        )
    }

    override fun onAcknowledged(generation: Long, connectionToken: Long) {
        acknowledgements.offer(generation to connectionToken)
    }

    companion object {
        const val WATCHDOG_MS = 5_000L
        const val GENERATION = 1L
    }
}
