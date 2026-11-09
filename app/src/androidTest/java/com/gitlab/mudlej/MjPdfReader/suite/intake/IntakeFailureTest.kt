// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.intake

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.ActivityWatcher
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ReaderProbe
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.state.DocumentLoadState
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class IntakeFailureTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()

    @Test
    @TestId("INTAKE-18")
    fun aZeroByteFileReportsThatItCannotBeIdentified() {
        val fixture = IntakeFixtures.copyInto(rules.testDirectory.directory(), IntakeFixtures.EMPTY)
        assertEquals("the empty fixture must be zero bytes", 0L, fixture.length())
        val since = IntakeLogcat.mark()

        ActivityScenario.launch<MainActivity>(IntakeReader.fileViewIntent(fixture)).use {
            reader.awaitShown()
            val state = reader.awaitLoadSettled()
            ReaderProbe.awaitVisibility(R.id.progressBar, View.GONE)
            ReaderProbe.assertVisibilityHolds(R.id.progressBar, View.GONE)

            IntakeLogcat.awaitErrorContaining("DocumentLoader", HASH_ERROR_MARKER, since)

            assertEquals(
                "a document whose identity cannot be computed must end in a failed load",
                DocumentLoadState.FAILED,
                state.loadState,
            )
            IntakeDb.holdEmpty()
        }
    }

    @Test
    @TestId("INTAKE-29")
    fun aCorruptPdfReportsAFileErrorAndRecordsNothing() {
        val fixture = IntakeFixtures.seeded(IntakeFixtures.CORRUPT)

        ActivityScenario.launch<MainActivity>(IntakeReader.fileViewIntent(fixture)).use {
            reader.awaitShown()
            val state = reader.awaitState("the document load reports failed", Await.LONG_TIMEOUT_MS) {
                it.loadState == DocumentLoadState.FAILED
            }
            ReaderProbe.awaitVisibility(R.id.progressBar, View.GONE)
            ReaderProbe.assertVisibilityHolds(R.id.progressBar, View.GONE)

            Snackbars.awaitText(IntakeDialogs.string(R.string.file_opening_error))
            assertEquals("a document that never loaded has no pages", 0 as Int?, state.pageCount)
            IntakeDb.holdEmpty()
        }
    }

    @Test
    @TestId("INTAKE-30")
    fun aNonPdfFileWithAPdfNameReportsAFileError() {
        val fixture = IntakeFixtures.copyInto(rules.testDirectory.directory(), IntakeFixtures.NOT_A_PDF)

        ActivityScenario.launch<MainActivity>(IntakeReader.fileViewIntent(fixture)).use {
            reader.awaitShown()
            reader.awaitState("the document load reports failed", Await.LONG_TIMEOUT_MS) {
                it.loadState == DocumentLoadState.FAILED
            }

            Snackbars.awaitText(IntakeDialogs.string(R.string.file_opening_error))
            IntakeDb.holdEmpty()
        }
    }

    @Test
    @TestId("INTAKE-31")
    fun aMissingFileOffersToLocateItAndExitsWhenDeclined() {
        val missing = File(rules.testDirectory.directory(), "gone.pdf")
        assertTrue("the fixture path must not exist", !missing.exists())

        ActivityWatcher().use { watcher ->
            IntakeReader.startFromOutside(IntakeReader.fileViewIntent(missing))
            Screens.awaitResumed(MainActivity::class.java, Await.LONG_TIMEOUT_MS)

            IntakeDialogs.awaitShowing(IntakeDialogs.string(R.string.stale_shared_title), Await.LONG_TIMEOUT_MS)
            assertTrue(
                "the locate action must be offered alongside the stale document title",
                IntakeDialogs.isShowing(IntakeDialogs.string(R.string.stale_shared_locate)),
            )

            IntakeDialogs.tap(IntakeDialogs.string(R.string.cancel))

            Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
            assertTrue(
                "declining recovery must finish the reader, observed ${watcher.observed()}",
                watcher.wasFinishingWhenDestroyed(MainActivity::class.java),
            )
            Await.until("the app has no resumed activity left") { Screens.resumedName() == null }
            IntakeDb.assertNoRecords()
        }
    }

    private companion object {
        const val HASH_ERROR_MARKER = "showFailedToComputeHashError"
    }
}
