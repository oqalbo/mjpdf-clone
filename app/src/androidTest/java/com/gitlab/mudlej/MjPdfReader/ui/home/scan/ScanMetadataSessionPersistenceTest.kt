// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home.scan

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gitlab.mudlej.MjPdfReader.core.debug.MetadataWorkerTestHooks
import com.gitlab.mudlej.MjPdfReader.core.debug.ScanDebugSignals
import com.gitlab.mudlej.MjPdfReader.data.AppDatabase
import com.gitlab.mudlej.MjPdfReader.data.PdfRepository
import com.gitlab.mudlej.MjPdfReader.data.entity.ScannedPdfEntry
import com.gitlab.mudlej.MjPdfReader.data.entity.ScannedPdfMetadataFailure
import com.gitlab.mudlej.MjPdfReader.data.entity.ScannedPdfMetadataState
import com.gitlab.mudlej.MjPdfReader.data.entity.SourceFingerprint
import com.gitlab.mudlej.MjPdfReader.data.entity.fingerprint
import com.gitlab.mudlej.MjPdfReader.ui.home.metadata.IPdfMetadataCallback
import com.gitlab.mudlej.MjPdfReader.ui.home.metadata.IPdfMetadataService
import com.gitlab.mudlej.MjPdfReader.ui.home.metadata.MetadataMemorySample
import com.gitlab.mudlej.MjPdfReader.ui.home.metadata.MetadataProtocol
import com.gitlab.mudlej.MjPdfReader.ui.home.metadata.PdfMetadataClient
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScanMetadataSessionPersistenceTest {
    @Test
    fun unexpectedProcessingDeathPersistsAndRetriesFirstBeforeLaterWork() = runBlocking {
        withFixture {
            seed(first, second)
            connect()

            session.workerDied(death(expected = false), repository)

            val retry = first.copy(metadataWorkerDeaths = 1)
            assertEquals(listOf(retry, second), repository.findAllScannedPdfs())
            assertEquals(listOf(retry, second), repository.findPendingScannedPdfs())
            assertNull(work.inFlight)
            assertEquals(listOf(first.fingerprint), worker.processCalls)
            assertCrashCounts(first, deaths = 1, retries = 1)
            assertCrashCounts(second, deaths = 0, retries = 0)

            reconnect()
            assertEquals(first.fingerprint, work.inFlight)
            assertEquals(listOf(first.fingerprint, first.fingerprint), worker.processCalls)

            session.workerDied(death(expected = false), repository)

            val failed = retry.copy(
                metadataState = ScannedPdfMetadataState.FAILED,
                metadataFailure = ScannedPdfMetadataFailure.WORKER_CRASH,
                metadataWorkerDeaths = 2,
            )
            assertEquals(listOf(failed, second), repository.findAllScannedPdfs())
            assertEquals(listOf(second), repository.findPendingScannedPdfs())
            assertCrashCounts(first, deaths = 2, retries = 1)
            reconnect()
            assertEquals(second.fingerprint, work.inFlight)
            assertEquals(listOf(first.fingerprint, first.fingerprint, second.fingerprint), worker.processCalls)
            assertCrashCounts(second, deaths = 0, retries = 0)
        }
    }

    @Test
    fun unexpectedDeathAwaitingAckDoesNotCrashOrRetryCommittedRow() = runBlocking {
        withFixture {
            seed(first, second)
            connect()
            prepareAndAcknowledge(result())
            val committed = first.copy(
                hash = "computed", pageCount = 12, metadataState = ScannedPdfMetadataState.COMPLETE,
            )
            assertEquals(listOf(committed, second), repository.findAllScannedPdfs())
            assertEquals(listOf(first.fingerprint to MetadataProtocol.ACK_CONTINUE), worker.acknowledgements)
            assertEquals(first.fingerprint, work.inFlight)
            assertTrue(session.blocksCompletion)

            session.workerDied(death(expected = false), repository)

            assertEquals(listOf(committed, second), repository.findAllScannedPdfs())
            assertEquals(listOf(second), repository.findPendingScannedPdfs())
            assertNull(work.inFlight)
            assertCrashCounts(first, deaths = 0, retries = 0)
            reconnect()
            assertEquals(second.fingerprint, work.inFlight)
            assertEquals(listOf(first.fingerprint, second.fingerprint), worker.processCalls)

            finishDiscovery()
            val later = result(second)
            prepareAndAcknowledge(later)
            session.workerAcknowledged(ScanEvent.WorkerAcknowledged(later.generation, later.connectionToken))
            assertEquals(
                listOf(first, second).map {
                    it.copy(hash = "computed", pageCount = 12, metadataState = ScannedPdfMetadataState.COMPLETE)
                },
                repository.findAllScannedPdfs(),
            )
            assertTrue(repository.findPendingScannedPdfs().isEmpty())
            assertCrashCounts(first, deaths = 0, retries = 0)
            assertCrashCounts(second, deaths = 0, retries = 0)
        }
    }

    @Test
    fun expectedStopDeathBeforeAckAwaitsCompletionInlineExactlyOnceAndIgnoresLateAck() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var completions = 0
        withFixture(completion = {
            completions++
            entered.complete(Unit)
            release.await()
        }) {
            seed(first)
            finishDiscovery()
            connect()
            val result = result()
            prepareAndAcknowledge(result)
            assertEquals(listOf(first.fingerprint to MetadataProtocol.ACK_STOP), worker.acknowledgements)
            val died = death(expected = true)

            val dying = async(start = CoroutineStart.UNDISPATCHED) {
                session.workerDied(died, repository)
            }
            try {
                assertTrue("STOP death must enter completion inline", entered.isCompleted)
                assertFalse("STOP death must await completion", dying.isCompleted)
                assertNull(work.inFlight)
                assertFalse(session.blocksCompletion)
            } finally {
                release.complete(Unit)
            }
            withTimeout(5_000) { dying.await() }

            session.workerAcknowledged(ScanEvent.WorkerAcknowledged(result.generation, result.connectionToken))
            session.workerDied(died, repository)
            assertEquals(1, completions)
            assertEquals(
                listOf(first.copy(hash = "computed", pageCount = 12, metadataState = ScannedPdfMetadataState.COMPLETE)),
                repository.findAllScannedPdfs(),
            )
            assertTrue(repository.findPendingScannedPdfs().isEmpty())
            assertEquals(listOf(first.fingerprint), worker.processCalls)
            assertCrashCounts(first, deaths = 0, retries = 0)
        }
    }

    @Test
    fun expectedRecycleDeathBeforeAckSchedulesReconnectAndIgnoresLateAck() = runBlocking {
        withFixture {
            seed(first, second)
            finishDiscovery()
            connect()
            val recycling = result().let { it.copy(sample = it.sample.copy(lowMemory = true)) }
            prepareAndAcknowledge(recycling)
            assertEquals(listOf(first.fingerprint to MetadataProtocol.ACK_RECYCLE), worker.acknowledgements)

            session.workerDied(death(expected = true), repository)

            assertNull(work.inFlight)
            assertFalse(session.blocksCompletion)
            assertCrashCounts(first, deaths = 0, retries = 0)
            assertEquals(listOf(second), repository.findPendingScannedPdfs())
            val lateAck = ScanEvent.WorkerAcknowledged(recycling.generation, recycling.connectionToken)
            session.workerAcknowledged(lateAck)
            assertEquals(listOf(first.fingerprint), worker.processCalls)

            reconnect()
            assertEquals(second.fingerprint, work.inFlight)
            assertEquals(listOf(first.fingerprint, second.fingerprint), worker.processCalls)
            val later = result(second)
            prepareAndAcknowledge(later)
            session.workerAcknowledged(lateAck)
            assertEquals(second.fingerprint, work.inFlight)
            assertTrue(session.blocksCompletion)

            session.workerAcknowledged(ScanEvent.WorkerAcknowledged(later.generation, later.connectionToken))
            assertNull(work.inFlight)
            assertFalse(session.blocksCompletion)
            assertEquals(
                listOf(first, second).map {
                    it.copy(hash = "computed", pageCount = 12, metadataState = ScannedPdfMetadataState.COMPLETE)
                },
                repository.findAllScannedPdfs(),
            )
            assertTrue(repository.findPendingScannedPdfs().isEmpty())
            assertCrashCounts(first, deaths = 0, retries = 0)
            assertCrashCounts(second, deaths = 0, retries = 0)
        }
    }

    @Test
    fun staleAndDuplicateResultsCannotMutateRowsAndSourceChangedIsStillAcknowledged() = runBlocking {
        withFixture {
            val stored = first.copy(
                pageCount = 9, metadataFailure = ScannedPdfMetadataFailure.IO_ERROR, metadataWorkerDeaths = 1,
            )
            val rows = listOf(stored, second)
            seed(stored, second)
            connect()
            val result = result()
            val stale = listOf(
                result.copy(generation = work.generation - 1),
                result.copy(connectionToken = result.connectionToken + 1),
                result.copy(fingerprint = second.fingerprint),
                result.copy(fingerprint = first.fingerprint.copy(size = first.size + 1)),
                result.copy(fingerprint = first.fingerprint.copy(lastModified = first.lastModified + 1_000)),
            )
            for (rejected in stale) {
                assertNull(session.prepareResult(ScanEvent.WorkerResult(rejected), repository))
                assertEquals(rows, repository.findAllScannedPdfs())
                assertEquals(first.fingerprint, work.inFlight)
                assertTrue(worker.acknowledgements.isEmpty())
            }

            prepareAndAcknowledge(result.copy(sourceChanged = true))
            assertEquals(rows, repository.findAllScannedPdfs())
            assertEquals(listOf(first.fingerprint to MetadataProtocol.ACK_CONTINUE), worker.acknowledgements)
            assertTrue(session.blocksCompletion)

            for (duplicate in listOf(result, result.copy(failure = ScannedPdfMetadataFailure.INVALID_PDF))) {
                assertNull(session.prepareResult(ScanEvent.WorkerResult(duplicate), repository))
                session.acknowledge(duplicate, refreshPending = false)
                assertEquals(rows, repository.findAllScannedPdfs())
                assertEquals(first.fingerprint, work.inFlight)
                assertEquals(listOf(first.fingerprint to MetadataProtocol.ACK_CONTINUE), worker.acknowledgements)
            }

            session.workerAcknowledged(ScanEvent.WorkerAcknowledged(result.generation, result.connectionToken))
            assertEquals(second.fingerprint, work.inFlight)
            assertEquals(listOf(first.fingerprint, second.fingerprint), worker.processCalls)
            assertEquals(rows, repository.findAllScannedPdfs())
        }
    }

    @Test
    fun failedCommitLeavesPendingItemRetryFirstWithoutAcknowledgement() = runBlocking {
        withFixture {
            seed(first, second)
            connect()
            withContext(Dispatchers.IO) {
                database.openHelper.writableDatabase.execSQL(
                    """
                    CREATE TRIGGER reject_metadata_commit BEFORE UPDATE ON ScannedPdfCache
                    WHEN NEW.metadataState = 'COMPLETE'
                    BEGIN
                        SELECT RAISE(ABORT, 'injected metadata commit failure');
                    END
                    """.trimIndent()
                )
            }

            assertNull(session.prepareResult(ScanEvent.WorkerResult(result()), repository))

            assertEquals(listOf(first, second), repository.findAllScannedPdfs())
            assertEquals(listOf(first, second), repository.findPendingScannedPdfs())
            assertNull(work.inFlight)
            assertTrue(work.hasPending)
            assertFalse(session.blocksCompletion)
            assertTrue(worker.acknowledgements.isEmpty())
            assertCrashCounts(first, deaths = 0, retries = 0)
            withContext(Dispatchers.IO) {
                database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_metadata_commit")
            }

            reconnect()
            assertEquals(first.fingerprint, work.inFlight)
            assertEquals(listOf(first.fingerprint, first.fingerprint), worker.processCalls)
            val retry = result()
            prepareAndAcknowledge(retry)
            assertEquals(
                listOf(
                    first.copy(hash = "computed", pageCount = 12, metadataState = ScannedPdfMetadataState.COMPLETE),
                    second,
                ),
                repository.findAllScannedPdfs(),
            )
            assertEquals(listOf(first.fingerprint to MetadataProtocol.ACK_CONTINUE), worker.acknowledgements)
            session.workerAcknowledged(ScanEvent.WorkerAcknowledged(retry.generation, retry.connectionToken))
            assertEquals(second.fingerprint, work.inFlight)
            assertEquals(listOf(first.fingerprint, first.fingerprint, second.fingerprint), worker.processCalls)
            assertCrashCounts(first, deaths = 0, retries = 0)
        }
    }

    private suspend fun CoroutineScope.withFixture(
        completion: suspend () -> Unit = {},
        test: suspend MetadataSessionFixture.() -> Unit,
    ) {
        MetadataWorkerTestHooks.reset()
        val fixture = MetadataSessionFixture(this, completion)
        try {
            fixture.test()
        } finally {
            fixture.session.cancelTimers()
            fixture.session.resetForGeneration()
            fixture.database.close()
            MetadataWorkerTestHooks.reset()
        }
    }
}

private class MetadataSessionFixture(scope: CoroutineScope, completion: suspend () -> Unit) {
    private val baseContext = InstrumentationRegistry.getInstrumentation().targetContext
    val database = Room.inMemoryDatabaseBuilder(baseContext, AppDatabase::class.java).build()
    val repository = PdfRepository(database)
    private val root = "/scan-session/${UUID.randomUUID()}"
    val first = ScannedPdfEntry("$root/a.pdf", 100, 1_000, "stored")
    val second = ScannedPdfEntry("$root/b.pdf", 200, 2_000, "stored")
    val work = ScanWorkIndex().also {
        it.advanceGeneration(10)
        it.restore(emptyList(), null)
    }
    val worker = RecordingMetadataService()
    private val memory = MetadataMemorySample(1_000_000_000, 100_000_000, 2_000_000_000, false, 10, 10)
    private val events = Channel<ScanEvent>(Channel.UNLIMITED)
    private val post: (ScanEvent) -> Unit = { events.trySend(it).getOrThrow() }
    private val component = ComponentName("test", "metadata")
    private lateinit var binding: ServiceConnection
    private val context = object : ContextWrapper(baseContext) {
        override fun getApplicationContext(): Context = this

        override fun bindService(service: Intent, connection: ServiceConnection, flags: Int): Boolean {
            binding = connection
            connection.onServiceConnected(component, worker)
            return true
        }

        override fun unbindService(connection: ServiceConnection) = Unit
    }
    val session = ScanMetadataSession(work, ScanPublication(work), PdfMetadataClient(context, post), scope, post, completion)

    suspend fun seed(vararg rows: ScannedPdfEntry) {
        repository.persistDiscoveredPdfs(rows.toList())
        work.restore(repository.findAllScannedPdfs(), null)
        work.rebuildPending(repository.findPendingScannedPdfs())
    }

    fun connect() {
        session.ensureWorker()
        val (generation, token) = checkNotNull(worker.registration)
        assertEquals(work.generation, generation)
        session.workerConnected(ScanEvent.WorkerConnected(generation, token, 21, 22, memory))
    }

    suspend fun reconnect() {
        val event = withTimeout(5_000) { events.receive() }
        assertEquals(ScanEvent.ReconnectDeadline(work.generation), event)
        connect()
    }

    suspend fun death(expected: Boolean): ScanEvent.WorkerDied {
        binding.onServiceDisconnected(component)
        val event = withTimeout(5_000) { events.receive() }
        val (generation, token) = checkNotNull(worker.registration)
        assertEquals(ScanEvent.WorkerDied(generation, token, expected), event)
        return event as ScanEvent.WorkerDied
    }

    fun finishDiscovery() {
        DiscoverySource.entries.forEach {
            work.sourceFinished(ScanEvent.DiscoverySourceFinished(work.generation, it))
        }
    }

    fun result(entry: ScannedPdfEntry = first): MetadataResult {
        val (generation, token) = checkNotNull(worker.registration)
        return MetadataResult(
            generation = generation,
            connectionToken = token,
            fingerprint = entry.fingerprint,
            hash = "computed",
            pageCount = 12,
            failure = null,
            sourceChanged = false,
            sample = memory,
            workerPid = 21,
            workerInstance = 22,
            maxActiveDocuments = 1,
        )
    }

    suspend fun prepareAndAcknowledge(result: MetadataResult) {
        val prepared = session.prepareResult(ScanEvent.WorkerResult(result), repository)
        assertEquals(result, prepared)
        session.acknowledge(checkNotNull(prepared), refreshPending = false)
    }

    fun assertCrashCounts(entry: ScannedPdfEntry, deaths: Int, retries: Int) {
        assertEquals(deaths, ScanDebugSignals.workerDeathCount(entry.fingerprint))
        assertEquals(retries, ScanDebugSignals.workerRetryCount(entry.fingerprint))
    }
}

private class RecordingMetadataService : IPdfMetadataService.Stub() {
    var registration: Pair<Long, Long>? = null
    val processCalls = mutableListOf<SourceFingerprint>()
    val acknowledgements = mutableListOf<Pair<SourceFingerprint, Int>>()

    override fun registerCallback(callback: IPdfMetadataCallback?, generation: Long, connectionToken: Long) {
        registration = generation to connectionToken
    }

    override fun sampleMemory(generation: Long, connectionToken: Long) = Unit

    override fun process(
        generation: Long,
        connectionToken: Long,
        path: String,
        size: Long,
        lastModified: Long,
        debugBehavior: Int,
    ) {
        processCalls += SourceFingerprint(path, size, lastModified)
    }

    override fun acknowledge(
        generation: Long,
        connectionToken: Long,
        path: String,
        size: Long,
        lastModified: Long,
        action: Int,
    ) {
        acknowledgements += SourceFingerprint(path, size, lastModified) to action
    }
}
