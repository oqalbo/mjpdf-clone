// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.home

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeActivity
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
class OpenFromHomeTest {

    @get:Rule
    val rules = SuiteRules.standard()

    @get:Rule
    val homeCleanup = HomeCleanupRule()

    private val home = HomeRobot()
    private val reader = ReaderRobot()

    @Test
    @TestId("HOME-21")
    fun tappingALibraryBookOpensItInTheReaderWithItsRecord() {
        val library = HomeBaselines.scopedLibrary(
            rules.testDirectory.directory(),
            BookSeed(
                HomeFixtures.EIGHT_PAGE,
                name = HomeFixtures.ALPHA,
                pageNumber = 4,
                pages = HomeFixtures.EIGHT_PAGE_PAGES,
                lastOpened = daysAgo(0),
            ),
        )

        home.launchFromLauncher()

        val grid = home.library()
        grid.awaitContains("alpha")

        grid.cell("alpha").click()
        Screens.awaitResumed(MainActivity::class.java)
        reader.awaitShown()
        reader.awaitLoaded()

        val intent = readerIntent()
        assertEquals(
            "the reader opens the document the record points at",
            library["alpha"].uri,
            intent.data,
        )
        assertTrue(
            "opening from home must mark the intent as coming from home",
            intent.getBooleanExtra(HomeActivity.EXTRA_FROM_HOME, false),
        )
        assertEquals(
            "the record hash travels with the intent",
            library.hashOf("alpha"),
            intent.getStringExtra(HomeActivity.EXTRA_RECORD_HASH),
        )
        reader.awaitPageIndex(4)
    }

    @Test
    @TestId("HOME-22")
    fun tappingAScanOnlyFileCreatesExactlyOneRecord() {
        val library = HomeBaselines.scopedLibrary(
            rules.testDirectory.directory(),
            BookSeed(HomeFixtures.BETA, record = false, pages = 1),
        )
        assertEquals("baseline: no records before the tap", 0, HomeLibrary.recordCount())

        home.launchFromLauncher()

        val grid = home.library()
        grid.awaitContains("beta")

        grid.cell("beta").click()
        Screens.awaitResumed(MainActivity::class.java)
        reader.awaitShown()
        reader.awaitLoaded()

        assertNull(
            "a scan only item passes a null hash, so the intent carries no recordHash extra",
            readerIntent().getStringExtra(HomeActivity.EXTRA_RECORD_HASH),
        )

        home.pressBack()
        Screens.awaitTopIs(HomeActivity::class.java)

        val records = HomeLibrary.awaitRecordCount(1)
        val record = records.single()
        assertTrue(
            "the new row must carry a content identity, read ${record.hash}",
            record.hash.startsWith(IDENTITY_PREFIX),
        )
        assertTrue(
            "the new row must not be filed under the synthetic scan key, read ${record.hash}",
            !record.hash.startsWith(SCAN_KEY_PREFIX),
        )
        assertEquals(
            "the new row must be filed under the identity of the file that was opened",
            library.hashOf("beta"),
            record.hash,
        )
        assertEquals(
            "the new row must point at the file that was tapped",
            Uri.fromFile(library.fileOf("beta")),
            record.uri,
        )
    }

    private fun readerIntent(): Intent = TestEnvironment.onMainThread {
        TestEnvironment.resumedActivityOfType(MainActivity::class.java)?.intent
            ?: throw AssertionError("MainActivity is not resumed, so it has no intent to read")
    }

    private companion object {
        const val IDENTITY_PREFIX = "h2"
        const val SCAN_KEY_PREFIX = "p"
    }
}
