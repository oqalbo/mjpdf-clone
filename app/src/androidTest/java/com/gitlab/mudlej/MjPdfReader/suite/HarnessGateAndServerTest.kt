// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.CrashGate
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.StubSharerProvider
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.net.LocalPdfServer
import com.gitlab.mudlej.MjPdfReader.suite.rules.AppCrashedDuringTestException
import com.gitlab.mudlej.MjPdfReader.suite.rules.CrashGateRule
import com.gitlab.mudlej.MjPdfReader.suite.rules.FailureArtifacts
import com.gitlab.mudlej.MjPdfReader.suite.rules.IntentionalKills
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.gitlab.mudlej.MjPdfReader.suite.artifacts.ArtifactBytes
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.intake.DownloadsGuardRule
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class HarnessGateAndServerTest {

    @get:Rule
    val rules = SuiteRules.withoutCrashGate()

    private val server = LocalPdfServer()

    @After
    fun stopServer() {
        server.shutdown()
    }

    @After
    fun removeSyntheticProbeArtifacts() {
        SYNTHETIC_DESCRIPTIONS.forEach { methodName ->
            val syntheticId = "${HarnessGateAndServerTest::class.java.simpleName}.$methodName"
            runCatching { File(FailureArtifacts.ROOT_PATH, syntheticId).deleteRecursively() }
        }
    }

    @Test
    @TestId("WAVE0-01")
    fun theCrashGateFailsACleanLookingTestWhenPdfiumRanOnTheMainThread() {
        val gate = CrashGateRule()
        val description = describe("pdfiumProbe")
        val statement = gate.apply(
            statementThat { Log.e("PdfFile", "pdfium work on main thread: /sdcard/probe.pdf x1") },
            description,
        )

        val thrown = runCatching { statement.evaluate() }.exceptionOrNull()

        assertTrue(
            "The crash gate must fail a test whose body passed but which ran pdfium on the main thread, got $thrown",
            thrown is AppCrashedDuringTestException,
        )
        assertTrue(
            "The crash gate message must name the violation, got ${thrown?.message}",
            thrown?.message?.contains("pdfium ran on the main thread") == true,
        )
    }

    @Test
    @TestId("WAVE0-13")
    fun theCrashGateLetsTheAcceptedTouchLookupsRunOnTheMainThread() {
        CrashGate.ACCEPTED_MAIN_THREAD_PDFIUM_PATHS.forEach { path ->
            val gate = CrashGateRule()
            val statement = gate.apply(
                statementThat { Log.w("PdfFile", "pdfium work on main thread: $path x1") },
                describe("acceptedPdfiumProbe"),
            )
            val thrown = runCatching { statement.evaluate() }.exceptionOrNull()
            assertTrue(
                "$path answers a touch inline by ruling, so the crash gate must let it through, got $thrown",
                thrown == null,
            )
        }
    }

    @Test
    @TestId("WAVE0-02")
    fun theCrashGateLetsACleanTestThrough() {
        val gate = CrashGateRule()
        val statement = gate.apply(statementThat { }, describe("cleanProbe"))
        statement.evaluate()
    }

    @Test
    @TestId("WAVE0-03")
    fun theCrashGateHonoursTheIntentionalKillRegistry() {
        val fatalLine = "FATAL EXCEPTION: main"
        val processLine = "Process: ${TestEnvironment.targetPackage}, PID: ${android.os.Process.myPid()}"

        val unregistered = runCatching {
            CrashGateRule().apply(statementThat {
                Log.e("AndroidRuntime", fatalLine)
                Log.e("AndroidRuntime", processLine)
            }, describe("fatalProbe")).evaluate()
        }.exceptionOrNull()
        assertTrue(
            "An unregistered fatal exception must fail the crash gate, got $unregistered",
            unregistered is AppCrashedDuringTestException,
        )

        val registered = runCatching {
            CrashGateRule().apply(
                statementThat {
                    IntentionalKills.expect("probe kills the app on purpose")
                    Log.e("AndroidRuntime", fatalLine)
                    Log.e("AndroidRuntime", processLine)
                },
                describe("intentionalProbe"),
            ).evaluate()
        }.exceptionOrNull()
        assertEquals("A registered intentional kill must not fail the crash gate", null, registered)
    }

    @Test
    @TestId("WAVE0-27")
    fun fatalRecordsAreMatchedToTheExactAppProcess() {
        val app = TestEnvironment.targetPackage
        fun line(pid: Int, message: String) = "08-04 08:13:46.072 $pid $pid E AndroidRuntime: $message"
        val header = line(12046, "FATAL EXCEPTION: main")
        val process = line(12046, "Process: $app, PID: 12046")
        val interleaved = listOf(
            header,
            line(20000, "FATAL EXCEPTION: main"),
            line(20000, "Process: $app.test, PID: 20000"),
            process,
        ).joinToString("\n")
        assertEquals(listOf(header, process), CrashGateRule.fatalLines(interleaved, app))
        assertTrue(CrashGateRule.fatalLines(process, app).isEmpty())
        assertTrue(CrashGateRule.fatalLines("$header\n${process.replace(app, "$app.test")}", app).isEmpty())
        assertTrue(CrashGateRule.fatalLines("$header\n${process.replace("PID: 12046", "PID: 20000")}", app).isEmpty())
        val child = process.replace(app, "$app:worker")
        assertEquals(listOf(header, child), CrashGateRule.fatalLines("$header\n$child", app))
    }

    @Test
    @TestId("WAVE0-06")
    fun theLocalServerServesAPdfAndLogsEveryRequest() {
        server.start()
        val body = "%PDF-1.4 local server probe".toByteArray()
        val url = server.servePdfBytes("/probe.pdf", body)

        val connection = URL(url).openConnection() as HttpURLConnection
        val code = connection.responseCode
        val received = connection.inputStream.use { it.readBytes() }
        connection.disconnect()

        assertEquals("the served status code", 200, code)
        assertEquals("the served body", String(body), String(received))
        assertTrue("the server must bind to loopback, got $url", url.startsWith("http://127.0.0.1:"))

        val paths = server.drainRequestPaths()
        assertTrue("the request log must record the fetched path, got $paths", paths.contains("/probe.pdf"))
    }

    @Test
    @TestId("WAVE0-07")
    fun theLocalServerServesNotFoundAndRedirects() {
        server.start()
        val missing = server.serveNotFound("/missing.pdf")
        val target = server.servePdfBytes("/target.pdf", "%PDF-1.4 target".toByteArray())
        val redirect = server.serveRedirect("/start.pdf", "/target.pdf")

        val missingConnection = URL(missing).openConnection() as HttpURLConnection
        assertEquals("a missing document must answer 404", 404, missingConnection.responseCode)
        missingConnection.disconnect()

        val redirectConnection = URL(redirect).openConnection() as HttpURLConnection
        redirectConnection.instanceFollowRedirects = true
        val followed = redirectConnection.inputStream.use { String(it.readBytes()) }
        redirectConnection.disconnect()

        assertEquals("a redirect must land on the target body", "%PDF-1.4 target", followed)
        assertTrue("the redirect target url must be on loopback", target.startsWith("http://127.0.0.1:"))

        val loop = server.serveRedirectLoop("/loop.pdf")
        val loopConnection = URL(loop).openConnection() as HttpURLConnection
        loopConnection.instanceFollowRedirects = true
        val loopFailed = runCatching { loopConnection.inputStream.use { it.readBytes() } }.isFailure
        loopConnection.disconnect()
        assertTrue("a redirect loop must not resolve successfully", loopFailed)
    }

    @Test
    @TestId("WAVE0-09")
    fun theStubSharerProviderIsDeclaredInTheDebugManifest() {
        assertNotNull(
            "The stub provider must be declared in app/src/debug/AndroidManifest.xml",
            TestEnvironment.targetContext.packageManager.resolveContentProvider(StubSharerProvider.AUTHORITY, 0),
        )

        StubSharerProvider.clear()
        val body = "%PDF-1.4 stub sharer probe".toByteArray()
        StubSharerProvider.publish("shared.pdf", body)
        val staged = StubSharerProvider.resolveFile("shared.pdf")
        assertNotNull("publish must stage the bytes where the provider can find them", staged)
        assertEquals("the staged bytes must round trip", String(body), String(staged!!.readBytes()))

        val elsewhere = File(TestEnvironment.targetContext.cacheDir, "registered-elsewhere.pdf")
        elsewhere.writeBytes(body)
        StubSharerProvider.registerPath("huge.pdf", elsewhere.absolutePath)
        assertEquals(
            "registerPath must resolve a fixture from where it already sits",
            elsewhere.absolutePath,
            StubSharerProvider.resolveFile("huge.pdf")?.absolutePath,
        )

        StubSharerProvider.clear()
        elsewhere.delete()
    }

    @Test
    @TestId("WAVE0-26")
    @TestDuration(DurationCategory.NORMAL)
    fun downloadsCleanupWaitsForTheDestroyedReadersPendingWriter() {
        val normal = Fixtures.seededFixture("normal.pdf")
        val workload = Fixtures.seededFixture("large.pdf")
        assertTrue("the pending writer workload must exist", workload.isFile)
        val marker = File(android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS,
        ), "intake-guard-probe.pdf")
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        var writer: Job? = null
        val guard = DownloadsGuardRule()
        try {
            guard.apply(statementThat {
                ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(normal)).use { scenario ->
                    scenario.onActivity { activity ->
                        activity.lifecycle.addObserver(LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_DESTROY) release.countDown()
                        })
                        writer = activity.lifecycleScope.launch(Dispatchers.IO) {
                            started.countDown()
                            release.await()
                            repeat(32) { ArtifactBytes.sha256(workload) }
                            normal.copyTo(marker, overwrite = true)
                        }
                    }
                    assertTrue("the writer must enter its IO work", started.await(Await.LONG_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                }
            }, describe("downloadsPendingWriter")).evaluate()
            runBlocking { withTimeout(Await.LONG_TIMEOUT_MS) { writer?.join() } }
            assertFalse("cleanup must remove a file published by the cancelled reader's pending writer", marker.exists())
        } finally {
            release.countDown()
            runBlocking { withTimeout(Await.LONG_TIMEOUT_MS) { writer?.join() } }
            Fixtures.delete(marker)
        }
    }

    private fun describe(methodName: String): Description = Description.createTestDescription(
        HarnessGateAndServerTest::class.java,
        methodName,
    )

    private fun statementThat(body: () -> Unit): Statement = object : Statement() {
        override fun evaluate() = body()
    }

    private companion object {

        val SYNTHETIC_DESCRIPTIONS = listOf(
            "pdfiumProbe",
            "acceptedPdfiumProbe",
            "cleanProbe",
            "fatalProbe",
            "intentionalProbe",
        )
    }
}
