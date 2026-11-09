// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home.metadata

import android.os.RemoteException
import com.gitlab.mudlej.MjPdfReader.data.entity.SourceFingerprint
import com.gitlab.mudlej.MjPdfReader.support.MetadataBindingContext
import com.gitlab.mudlej.MjPdfReader.support.RecordingMetadataService
import com.gitlab.mudlej.MjPdfReader.ui.home.scan.ScanEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfMetadataClientTest {
    private val context = MetadataBindingContext()
    private val events = mutableListOf<ScanEvent>()
    private val client = PdfMetadataClient(context, events::add)
    private val service = RecordingMetadataService()
    private val fingerprint = SourceFingerprint("/books/one.pdf", 100, 1_000)

    @Test
    fun refusedBindingReportsOneUnexpectedDeath() {
        context.bindingAllowed = false

        assertFalse(client.connect(10, 20))
        context.connections.single().onServiceDisconnected(null)

        assertEquals(listOf(ScanEvent.WorkerDied(10, 20, false)), events)
        assertEquals(context.connections, context.unbound)
    }

    @Test
    fun bindingSecurityExceptionReportsOneUnexpectedDeath() {
        context.bindingFailure = SecurityException("denied")

        assertFalse(client.connect(10, 20))
        context.connections.single().onServiceDisconnected(null)

        assertEquals(listOf(ScanEvent.WorkerDied(10, 20, false)), events)
        assertEquals(context.connections, context.unbound)
    }

    @Test
    fun recycleAcknowledgementMakesTheFollowingDeathExpected() {
        connect()

        client.acknowledge(10, 20, fingerprint, MetadataProtocol.ACK_RECYCLE)
        context.connections.single().onServiceDisconnected(null)

        assertEquals(listOf(fingerprint to MetadataProtocol.ACK_RECYCLE), service.acknowledgements)
        assertEquals(listOf(ScanEvent.WorkerDied(10, 20, true)), events)
    }

    @Test
    fun continueAcknowledgementLeavesTheFollowingDeathUnexpected() {
        connect()

        client.acknowledge(10, 20, fingerprint, MetadataProtocol.ACK_CONTINUE)
        context.connections.single().onServiceDisconnected(null)

        assertEquals(listOf(fingerprint to MetadataProtocol.ACK_CONTINUE), service.acknowledgements)
        assertEquals(listOf(ScanEvent.WorkerDied(10, 20, false)), events)
    }

    @Test
    fun remoteProcessFailureRetiresTheConnectionAndReportsDeathOnlyOnce() {
        connect()
        service.processFailure = RemoteException("worker died")

        client.process(10, 20, fingerprint, MetadataProtocol.DEBUG_NONE)
        client.process(10, 20, fingerprint, MetadataProtocol.DEBUG_NONE)
        context.connections.single().onServiceDisconnected(null)

        assertEquals(listOf(fingerprint to MetadataProtocol.DEBUG_NONE), service.processCalls)
        assertEquals(listOf(ScanEvent.WorkerDied(10, 20, false)), events)
        assertEquals(context.connections, context.unbound)
    }

    @Test
    fun staleGenerationAndTokenCannotCallOrMarkTheCurrentWorkerExpectedToDie() {
        connect()

        for ((generation, token) in listOf(9L to 20L, 10L to 19L)) {
            client.process(generation, token, fingerprint, MetadataProtocol.DEBUG_NONE)
            client.acknowledge(generation, token, fingerprint, MetadataProtocol.ACK_RECYCLE)
            client.sampleMemory(generation, token)
        }

        assertEquals(emptyList<Any>(), service.processCalls)
        assertEquals(emptyList<Any>(), service.acknowledgements)
        assertEquals(emptyList<Any>(), service.memoryRequests)
        assertEquals(emptyList<ScanEvent>(), events)
        client.process(10, 20, fingerprint, MetadataProtocol.DEBUG_NONE)
        assertEquals(listOf(fingerprint to MetadataProtocol.DEBUG_NONE), service.processCalls)
        context.connections.single().onServiceDisconnected(null)
        assertEquals(listOf(ScanEvent.WorkerDied(10, 20, false)), events)
    }

    @Test
    fun expectedDisconnectAndLateDisconnectionEmitNothing() {
        connect()

        client.disconnect(expected = true)
        context.connections.single().onServiceDisconnected(null)

        assertEquals(emptyList<ScanEvent>(), events)
        assertEquals(context.connections, context.unbound)
    }

    @Test
    fun replacedConnectionCannotRegisterOrRetireTheNewWorker() {
        assertTrue(client.connect(9, 19))
        val old = context.connections.single()
        assertTrue(client.connect(10, 20))
        val oldService = RecordingMetadataService()

        old.onServiceConnected(null, oldService)
        old.onServiceDisconnected(null)
        context.connections.last().onServiceConnected(null, service)
        client.process(10, 20, fingerprint, MetadataProtocol.DEBUG_NONE)

        assertNull(oldService.registration)
        assertEquals(listOf(old), context.unbound)
        assertEquals(10L to 20L, service.registration)
        assertEquals(listOf(fingerprint to MetadataProtocol.DEBUG_NONE), service.processCalls)
        assertEquals(emptyList<ScanEvent>(), events)
    }

    @Test
    fun deathLinkFailureReportsDeathWithoutRegisteringACallback() {
        assertTrue(client.connect(10, 20))
        service.linkFailure = RemoteException("dead binder")

        context.connections.single().onServiceConnected(null, service)
        context.connections.single().onServiceDisconnected(null)

        assertNull(service.registration)
        assertEquals(listOf(ScanEvent.WorkerDied(10, 20, false)), events)
        assertEquals(context.connections, context.unbound)
    }

    private fun connect() {
        assertTrue(client.connect(10, 20))
        context.connections.single().onServiceConnected(null, service)
        assertEquals(10L to 20L, service.registration)
    }
}
