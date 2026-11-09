// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.zoom

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.AlwaysFinishActivitiesRule
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.data.AppDatabase
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class ZoomPersistenceTest : ZoomTestCase() {

    @get:Rule
    val alwaysFinishActivities = AlwaysFinishActivitiesRule()

    @Test
    @TestId("ZOOM-36")
    fun zoomSurvivesRotation() {
        openReader {
            ReaderCanvas.doubleTapCentre()
            val before = settledFullQuality()
            assertZoom("the double tap must magnify before the rotation", MID_ZOOM, before.zoom!!)
            val instanceBefore = TestEnvironment.resumedActivityOfType(MainActivity::class.java)

            TestEnvironment.uiDevice.setOrientationLeft()
            Await.until("the reader lays out in landscape", Await.LONG_TIMEOUT_MS) {
                val reading = reader.readStateOrNull()
                reading != null && reading.viewWidth!! > reading.viewHeight!!
            }
            val after = settledFullQuality()

            assertSame(
                "the reader declares configChanges for orientation, so it must not be recreated",
                instanceBefore,
                TestEnvironment.resumedActivityOfType(MainActivity::class.java),
            )
            assertZoom("the zoom must survive an in place reconfiguration", MID_ZOOM, after.zoom!!)
            assertTrue(
                "the reading position must be preserved across the turn, moving from ${before.positionOffset} " +
                    "to ${after.positionOffset}",
                abs(after.positionOffset!! - before.positionOffset!!) <= POSITION_TOLERANCE,
            )
            assertEquals("the page must not change across a rotation", before.page, after.page)
        }
    }

    @Test
    @TestId("ZOOM-37")
    fun restoredZoomIsClampedByTheCurrentMaximumZoom() {
        SettingsSeeder.seed(Preferences.maxZoomKey, DEFAULT_MAX_ZOOM)

        val file = fixture(ZoomFixtures.NORMAL)
        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(file)).use { scenario ->
            awaitReaderReady()

            Pinch.byRatio(BIG_SPREAD_RATIO)
            settled()
            Pinch.byRatio(BIG_SPREAD_RATIO)
            val magnified = settled()
            assertTrue("the pinch must reach at least $DEEP_ZOOM, but read ${magnified.zoom}", magnified.zoom!! >= DEEP_ZOOM)
            val pageBefore = magnified.page

            SettingsSeeder.seed(Preferences.maxZoomKey, LOWERED_MAX_ZOOM)

            scenario.recreate()
            awaitReaderReady()
            val restored = settledFullQuality()

            assertZoom("the restored zoom must be clamped to the current ceiling", LOWERED_MAX_ZOOM, restored.zoom!!)
            assertZoom("the applied ceiling must be the newly seeded one", LOWERED_MAX_ZOOM, restored.maxZoom!!)
            assertEquals("the restore must keep the page it was on", pageBefore, restored.page)
            Await.assertThroughout(
                condition = "the restored zoom never rises above the lowered ceiling",
                windowMs = SETTLE_WINDOW_MS,
            ) { state().zoom!! <= LOWERED_MAX_ZOOM + ZOOM_TOLERANCE }
        }
    }

    @Test
    @TestId("ZOOM-38")
    @TestDuration(DurationCategory.NORMAL)
    fun zoomDoesNotPersistAcrossSessions() {
        val file = fixture(ZoomFixtures.NORMAL)

        launchAt(file)
        ReaderCanvas.placeViewportForSetup(3)
        reader.awaitPageIndex(3)
        settledFullQuality()
        ReaderCanvas.doubleTapCentre()
        assertZoom("the double tap must magnify before leaving", MID_ZOOM, settledFullQuality().zoom!!)

        TestEnvironment.uiDevice.pressBack()
        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)

        launchAt(file)
        val reopened = settledFullQuality()

        assertZoom("reopening a document must give a normal sized page again", RESTING_ZOOM, reopened.zoom!!)
        assertNoZoomInPreferences()
        assertNoZoomColumnOnPdfRecord()
    }

    @Test
    @TestId("ZOOM-39")
    @TestDuration(DurationCategory.NORMAL)
    fun zoomIsKeptAcrossAPageTurn() {
        openReader {
            ReaderCanvas.doubleTapCentre()
            assertZoom("the double tap must magnify before the page turn", MID_ZOOM, settledFullQuality().zoom!!)

            val after = dragForwardUntilPage(2, PAGE_DRAG_ATTEMPTS)

            assertZoom("the zoom must be kept across a page turn", MID_ZOOM, after.zoom!!)
            assertEquals("the drag must reach the third page", 2 as Int?, after.page)
        }
    }

    @Test
    @TestId("ZOOM-43")
    fun inRtlHorizontalReadingALeftwardFlingGoesBack() {
        SettingsSeeder.seedAll(
            mapOf(
                Preferences.horizontalScrollKey to true,
                Preferences.pageFlingKey to true,
                Preferences.pageSnapKey to true,
            )
        )

        openReader(ZoomFixtures.ARABIC) {
            ReaderCanvas.placeViewportForSetup(START_PAGE)
            val before = reader.awaitPageIndex(START_PAGE)
            assertEquals("the document must be detected right to left", true as Boolean?, before.readingDirectionRtl)

            Swipe.fling(ReaderCanvas.at(0.85f, 0.5f), -RTL_FLING_STEP_PX, 0f)
            val after = settledFullQuality()

            assertEquals("a leftward fling in an RTL document must go one page back", (START_PAGE - 1) as Int?, after.page)
            assertNear(
                "the settled offset must sit on the snap offset of the page it went back to",
                -after.snapOffset!!,
                after.offsetX!!,
                SNAP_TOLERANCE_PX,
            )
        }
    }

    private fun launchAt(file: File) {
        TestEnvironment.targetContext.startActivity(
            ReaderRobot.viewIntent(file).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        Screens.awaitResumed(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        awaitReaderReady()
    }

    private fun assertNoZoomInPreferences() {
        val offenders = SettingsSeeder.preferences.all.keys
            .filter { it.contains("zoom", ignoreCase = true) }
            .filterNot { it == Preferences.maxZoomKey }
        assertTrue(
            "no SharedPreferences key may hold a zoom value, but the store holds $offenders",
            offenders.isEmpty(),
        )
    }

    private fun assertNoZoomColumnOnPdfRecord() {
        val database = AppDatabase.getInstance(TestEnvironment.targetContext)
        val columns = mutableListOf<String>()
        database.openHelper.readableDatabase.query("PRAGMA table_info(PdfRecord)").use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) columns.add(cursor.getString(nameColumn))
        }
        assertTrue("PdfRecord must carry at least the page column, but reads $columns", columns.contains("pageNumber"))
        assertTrue(
            "no PdfRecord column may hold a zoom value, but the table carries $columns",
            columns.none { it.contains("zoom", ignoreCase = true) },
        )
    }

    private companion object {
        const val POSITION_TOLERANCE = 0.05f
        const val BIG_SPREAD_RATIO = 2.5f
        const val DEEP_ZOOM = 6.0f
        const val LOWERED_MAX_ZOOM = 3.0f
        const val SETTLE_WINDOW_MS = 500L
        const val PAGE_DRAG_ATTEMPTS = 12
        const val START_PAGE = 2
        const val RTL_FLING_STEP_PX = 120f
        const val SNAP_TOLERANCE_PX = 2f
    }
}
