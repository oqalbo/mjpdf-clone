// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.lists

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Rotation
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.core.Toasts
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.userhighlights.UserHighlightsActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class ListChromeTest : ListsTestCase() {

    @Test
    @TestId("LIST-46")
    fun incognitoCarriesDarkChromeIntoBothLists() {
        val incognitoSurface = ContextCompat.getColor(TestEnvironment.targetContext, R.color.incognito_surface)
        openReader(ListsFixtures.RICH, incognito = true) { _, _ ->
            val list = openHighlightsAndScan()
            assertEquals(
                "the highlights list night mode",
                AppCompatDelegate.MODE_NIGHT_YES,
                list.localNightMode(),
            )
            assertTrue("the highlights list intent carries the incognito flag", list.launchIntentSaysIncognito())
            assertEquals(
                "the incognito theme overlay replaces colorSurface on the highlights list",
                incognitoSurface,
                list.resolvedSurfaceColor(),
            )

            list.pressBack()
            awaitBackInReader()

            val noteList = openNotesAndScan()
            assertEquals(
                "the notes list night mode",
                AppCompatDelegate.MODE_NIGHT_YES,
                noteList.localNightMode(),
            )
            assertTrue("the notes list intent carries the incognito flag", noteList.launchIntentSaysIncognito())
            assertEquals(
                "the incognito theme overlay replaces colorSurface on the notes list",
                incognitoSurface,
                noteList.resolvedSurfaceColor(),
            )
        }
    }

    @Test
    @TestId("LIST-47")
    fun theListOpensOnAPasswordProtectedDocument() {
        openReader(ListsFixtures.LOCKED, password = ListsFixtures.LOCKED_PASSWORD) { _, _ ->
            val list = openHighlightsAndScan()

            Toasts.assertNoneWithin(list.string(R.string.file_opening_error))
            assertTrue("the highlights list finished instead of listing", list.isShown())
            assertEquals("highlight rows on the locked document", LOCKED_GROUPS, list.itemCount())

            list.pressBack()
            awaitBackInReader()

            val noteList = openNotesAndScan()
            assertEquals("note rows on the locked document", LOCKED_NOTES, noteList.itemCount())
        }
    }

    @Test
    @TestId("LIST-48")
    @TestDuration(DurationCategory.SHORT)
    fun theListClosesCleanlyWhenTheFileIsGone() {
        Fixtures.ensureAllFilesAccess()
        val file = fixtureNamed(ListsFixtures.RICH, "list-48-rich.pdf")
        openReaderOn(file) { _, _ ->
            Fixtures.delete(file)
            Await.until("the fixture copy is gone from storage") { !file.exists() }

            chooseReaderAction(R.string.user_highlights_title)

            Toasts.awaitText(TestEnvironment.targetContext.getString(R.string.file_opening_error))
            Screens.awaitGone(UserHighlightsActivity::class.java, Await.LONG_TIMEOUT_MS)
            Screens.awaitTopIs(MainActivity::class.java, Await.LONG_TIMEOUT_MS)

            assertFalse(
                "a highlights list is still on screen with a stuck progress bar",
                Screens.isInTask(UserHighlightsActivity::class.java),
            )
        }
    }

    @Test
    @TestId("LIST-49")
    @TestDuration(DurationCategory.SHORT)
    fun theListSurvivesRotationWithoutDuplicatingRows() {
        openReader(ListsFixtures.RICH) { _, _ ->
            val list = openHighlightsAndScan()
            val pillsBefore = list.allPagePills()
            val chipsBefore = list.colorChipCount()

            Rotation.toLandscape(UserHighlightsActivity::class.java, Await.LONG_TIMEOUT_MS)
            list.awaitScanFinished()

            assertEquals("rows after rotation", ListsFixtures.richGroups.size, list.itemCount())
            assertEquals("the page pill order after rotation", pillsBefore, list.allPagePills())
            assertEquals("colour chips after rotation", chipsBefore, list.colorChipCount())
            assertEquals("the chip count is All plus one per colour", 5, list.colorChipCount())
        }
    }

    private companion object {
        const val LOCKED_GROUPS = 2
        const val LOCKED_NOTES = 1
    }
}
