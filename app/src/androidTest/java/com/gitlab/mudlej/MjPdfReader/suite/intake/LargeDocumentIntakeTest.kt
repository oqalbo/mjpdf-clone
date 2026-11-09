// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.intake

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.debug.ReaderTestHooks
import kotlinx.coroutines.CompletableDeferred
import android.content.res.Configuration
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.pdf.ReadingDirection
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.suite.state.DocumentLoadState
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class LargeDocumentIntakeTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()

    @Test
    @TestId("INTAKE-53")
    fun aRotationDuringLoadKeepsOneRecordAndOnePosition() {
        val fixture = IntakeFixtures.seeded(IntakeFixtures.LARGE)

        val barrier = CompletableDeferred<Unit>()
        ReaderTestHooks.documentLoadBarrier = barrier
        try {
            IntakeReader.startFromOutside(IntakeReader.fileViewIntent(fixture))
            Screens.awaitResumed(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
            reader.awaitShown(Await.LONG_TIMEOUT_MS)
            Await.until("the document load is held in flight") { ReaderTestHooks.isDocumentLoadBlocked() }
            reader.awaitState("the document is still loading") { it.loadState == DocumentLoadState.LOADING }

            TestEnvironment.uiDevice.setOrientationLeft()
            Await.until("the reader rotates while loading remains held") {
                TestEnvironment.onMainThread {
                    TestEnvironment.resumedActivityOfType(MainActivity::class.java)?.resources?.configuration?.orientation ==
                        Configuration.ORIENTATION_LANDSCAPE
                }
            }
            assertTrue("the load must remain blocked through rotation", ReaderTestHooks.isDocumentLoadBlocked())
            assertEquals(DocumentLoadState.LOADING, reader.readState().loadState)
        } finally {
            ReaderTestHooks.documentLoadBarrier = null
            barrier.complete(Unit)
        }

        reader.awaitLoaded(Await.LONG_TIMEOUT_MS)
        reader.awaitFullQualityRender(Await.LONG_TIMEOUT_MS)

        ReaderNavigation.jumpToPage(reader, DEEP_PAGE)

        TestEnvironment.uiDevice.setOrientationNatural()
        TestEnvironment.uiDevice.waitForIdle()
        reader.awaitPageIndex(DEEP_PAGE, Await.LONG_TIMEOUT_MS)
        reader.awaitFullQualityRender(Await.LONG_TIMEOUT_MS)

        val record = IntakeDb.awaitRecordCount(1, Await.LONG_TIMEOUT_MS).single()
        Await.untilValue(
            "the stored position reaches page index $DEEP_PAGE",
            Await.LONG_TIMEOUT_MS,
            read = { IntakeDb.record(record.hash)?.pageNumber },
        ) { it == DEEP_PAGE }
        IntakeDb.holdRecordCount(1)
    }

    @Test
    @TestId("INTAKE-54")
    fun aVeryLargeDocumentOpensAndReportsItsFullPageCount() {
        val fixture = IntakeFixtures.seeded(IntakeFixtures.LARGE)
        val lastPage = IntakeFixtures.LARGE_PAGES - 1

        ActivityScenario.launch<MainActivity>(IntakeReader.fileViewIntent(fixture)).use {
            reader.awaitShown(Await.LONG_TIMEOUT_MS)
            reader.awaitLoaded(Await.LONG_TIMEOUT_MS)
            val loaded = reader.awaitFullQualityRender(Await.LONG_TIMEOUT_MS)
            assertEquals(IntakeFixtures.LARGE_PAGES as Int?, loaded.pageCount)

            val state = ReaderNavigation.jumpToLastPage(reader)
            assertEquals("the deep jump must land on the last page", lastPage as Int?, state.page)

            val record = IntakeDb.awaitRecordCount(1, Await.LONG_TIMEOUT_MS).single()
            Await.untilValue(
                "the record records the page count and the last page",
                Await.LONG_TIMEOUT_MS,
                read = { IntakeDb.record(record.hash) },
            ) { it != null && it.length == IntakeFixtures.LARGE_PAGES && it.pageNumber == lastPage }
        }
    }

    @Test
    @TestId("INTAKE-55")
    fun anRtlDocumentOpensWithARightToLeftReadingDirection() {
        SettingsSeeder.seed(Preferences.horizontalScrollKey, true)
        val fixture = IntakeFixtures.seeded(IntakeFixtures.ARABIC)

        ActivityScenario.launch<MainActivity>(IntakeReader.fileViewIntent(fixture)).use {
            reader.awaitShown()
            reader.awaitLoaded(Await.LONG_TIMEOUT_MS)
            val state = reader.awaitFullQualityRender()
            assertEquals(IntakeFixtures.ARABIC_PAGES as Int?, state.pageCount)
            assertEquals(
                "an Arabic document must be laid out right to left",
                true,
                state.readingDirectionRtl,
            )
        }

        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)

        val record = IntakeDb.awaitRecordCount(1, Await.LONG_TIMEOUT_MS).single()
        Await.untilValue(
            "the detected reading direction reaches the record",
            Await.LONG_TIMEOUT_MS,
            read = { IntakeDb.record(record.hash)?.detectedReadingDirection },
        ) { it == ReadingDirection.RIGHT_TO_LEFT.id }
        assertNull(
            "nothing overrode the detection, so the override column stays empty",
            IntakeDb.record(record.hash)?.readingDirectionOverride,
        )
    }

    @Test
    @TestId("INTAKE-56")
    fun aPreviewCacheIsWrittenForANormalDocument() {
        val fixture = IntakeFixtures.seeded(IntakeFixtures.NORMAL)
        val identity = IntakeFixtures.expectedIdentity(fixture)

        ActivityScenario.launch<MainActivity>(IntakeReader.fileViewIntent(fixture)).use {
            reader.awaitShown()
            reader.awaitLoaded()
            reader.awaitFullQualityRender()
            ReaderNavigation.jumpToPage(reader, 1)

            val docKey = previewDocKey(identity, IntakeFixtures.NORMAL_PAGES, fixture.length())
            Await.until(
                "a preview keyed by $docKey reaches the preview cache",
                Await.LONG_TIMEOUT_MS,
            ) { IntakeReader.hasPreviewsFor(docKey) }
        }
    }

    @Test
    @TestId("INTAKE-56-protected")
    fun aPreviewCacheIsSkippedForAProtectedDocument() {
        val fixture = IntakeFixtures.seeded(IntakeFixtures.PROTECTED)
        val identity = IntakeFixtures.expectedIdentity(fixture)

        ActivityScenario.launch<MainActivity>(IntakeReader.fileViewIntent(fixture)).use {
            IntakeDialogs.awaitShowing(
                TestEnvironment.targetContext.getString(R.string.protected_pdf)
            )
            onView(withId(R.id.passwordInput)).perform(
                replaceText(IntakeFixtures.PROTECTED_PASSWORD),
                closeSoftKeyboard(),
            )
            Dialogs.tapPositive()

            reader.awaitLoaded(Await.LONG_TIMEOUT_MS)
            reader.awaitFullQualityRender()
            ReaderNavigation.jumpToPage(reader, 1)

            Await.assertThroughout(
                condition = "no preview is written for a password protected document",
                windowMs = IntakeDb.QUIET_WINDOW_MS,
            ) { !IntakeReader.hasPreviewsForIdentity(identity) }

            assertTrue(
                "the protected document must still be readable",
                (reader.readState().pageCount ?: 0) == IntakeFixtures.PROTECTED_PAGES,
            )
        }
    }

    private fun previewDocKey(identity: String, pageCount: Int, sizeBytes: Long): String =
        "$identity-$pageCount-$sizeBytes"

    private companion object {
        const val DEEP_PAGE = 12
    }
}
