// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.privacy

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class IncognitoBookmarkTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = PrivacyReader()
    private val home = PrivacyHome()

    @After
    fun leaveNoTaskBehind() {
        reader.finishTask()
        home.picker.dismissIfShown()
    }

    @Test
    @TestId("PRIV-13")
    fun addingABookmarkWhileIncognitoIsRefusedWithAnExplanatoryDialog() {
        PrivacyFixtures.file(PrivacyFixtures.NORMAL)

        ActivityScenario.launch<MainActivity>(PrivacyReader.incognitoViewIntent(PrivacyFixtures.NORMAL)).use {
            reader.awaitReady()
            reader.awaitIdentity()

            reader.tapBookmarkTile()

            awaitDialogText(string(R.string.bookmark_incognito_blocked_title))
            onView(withText(string(R.string.bookmark_incognito_blocked_message)))
                .inRoot(isDialog())
                .check(matches(isDisplayed()))
            onView(withText(string(R.string.incognito_exit)))
                .inRoot(isDialog())
                .check(matches(isDisplayed()))
            assertFalse(
                "the history off wording must not be used for the incognito case",
                TestEnvironment.uiDevice.hasObject(By.text(string(R.string.history_action_blocked))),
            )

            onView(withText(string(R.string.cancel))).inRoot(isDialog()).perform(click())
            Await.until("the refusal dialog is dismissed") {
                !TestEnvironment.uiDevice.hasObject(By.text(string(R.string.bookmark_incognito_blocked_title)))
            }

            PrivacyStores.holdBookmarkCount(0)
            assertEquals(
                "a refused bookmark must leave no UserBookmark row",
                0,
                PrivacyStores.bookmarkCount(),
            )
            assertEquals(
                "the bookmark action must still offer to add rather than to remove",
                string(R.string.add_bookmark),
                reader.readBookmarkTileLabel(),
            )
            assertTrue(
                "dismissing the refusal must leave the session incognito",
                reader.isIncognitoIconVisible(),
            )
        }
    }

    @Test
    @TestId("PRIV-39")
    fun removingAnExistingBookmarkWhileIncognitoIsAllowedByDesign() {
        val fixture = PrivacyFixtures.file(PrivacyFixtures.NORMAL)
        val hash = PrivacyFixtures.identityOf(fixture)
        home.listByFileName()

        ActivityScenario.launch<MainActivity>(PrivacyReader.viewIntent(PrivacyFixtures.NORMAL)).use {
            reader.awaitReady()
            reader.awaitIdentity()
            reader.jumpToPageIndex(BOOKMARK_PAGE_INDEX)
            reader.tapBookmarkTile()
        }

        val seeded = Await.untilValue(
            "the normal session records one bookmark",
            Await.LONG_TIMEOUT_MS,
            read = { PrivacyStores.bookmarks() },
        ) { it.size == 1 }
        assertEquals(
            "the seeded bookmark must sit on the page it was added from",
            BOOKMARK_PAGE_INDEX,
            seeded.single().pageIndex,
        )

        home.launch()
        home.openInIncognitoFromOptions(PrivacyFixtures.NORMAL_TITLE)
        reader.awaitReady()
        reader.awaitIncognitoIcon(visible = true)
        reader.awaitIdentity()
        reader.jumpToPageIndex(BOOKMARK_PAGE_INDEX)

        reader.tapBookmarkTile()

        Await.untilValue(
            "the removal is written through even while incognito",
            Await.LONG_TIMEOUT_MS,
            read = { PrivacyStores.bookmarkCount() },
        ) { it == 0 }
        assertFalse(
            "no incognito refusal dialog may appear for a removal",
            TestEnvironment.uiDevice.hasObject(By.text(string(R.string.bookmark_incognito_blocked_title))),
        )
        assertFalse(
            "no history off refusal may appear for a removal",
            TestEnvironment.uiDevice.hasObject(By.text(string(R.string.history_action_blocked))),
        )
        reader.finishTask()

        ActivityScenario.launch<MainActivity>(PrivacyReader.viewIntent(PrivacyFixtures.NORMAL)).use {
            reader.awaitReady()
            reader.awaitIdentity()
            reader.jumpToPageIndex(BOOKMARK_PAGE_INDEX)
            assertEquals(
                "reopening normally must show the page as unbookmarked",
                string(R.string.add_bookmark),
                reader.readBookmarkTileLabel(),
            )
        }
        assertEquals(
            "the bookmark for $hash must be gone",
            0,
            PrivacyStores.bookmarks().count { it.fileHash == hash },
        )
    }

    @Test
    @TestId("PRIV-41")
    fun exitIncognitoFromTheBookmarkDialogConvertsTheSessionAndRecordsTheBookmark() {
        val fixture = PrivacyFixtures.file(PrivacyFixtures.NORMAL)
        val hash = PrivacyFixtures.identityOf(fixture)

        ActivityScenario.launch<MainActivity>(PrivacyReader.incognitoViewIntent(PrivacyFixtures.NORMAL)).use {
            reader.awaitReady()
            reader.awaitIdentity()
            reader.jumpToPageIndex(BOOKMARK_PAGE_INDEX)

            reader.tapBookmarkTile()
            awaitDialogText(string(R.string.bookmark_incognito_blocked_title))
            onView(withText(string(R.string.incognito_exit))).inRoot(isDialog()).perform(click())

            reader.awaitIncognitoIcon(visible = false, timeoutMs = Await.LONG_TIMEOUT_MS)
            reader.awaitReady()
            assertFalse(
                "the incognito treatment must be gone once the offer is taken",
                reader.isIncognitoIconVisible(),
            )

            reader.jumpToPageIndex(BOOKMARK_PAGE_INDEX)
            reader.tapBookmarkTile()

            val bookmarks = Await.untilValue(
                "the converted session records the bookmark",
                Await.LONG_TIMEOUT_MS,
                read = { PrivacyStores.bookmarks() },
            ) { it.size == 1 }
            assertEquals(
                "the bookmark must be keyed on the document identity",
                hash,
                bookmarks.single().fileHash,
            )
            assertEquals(
                "the bookmark must sit on the page the user was reading",
                BOOKMARK_PAGE_INDEX,
                bookmarks.single().pageIndex,
            )
        }

        val rows = PrivacyStores.awaitRecordCount(1, Await.LONG_TIMEOUT_MS)
        assertTrue(
            "normal recording must resume for the rest of the session",
            rows.any { it.hash == hash },
        )
    }

    private fun awaitDialogText(text: String) {
        Dialogs.awaitText(text)
        onView(withText(text)).inRoot(isDialog()).check(matches(isDisplayed()))
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    private companion object {
        const val BOOKMARK_PAGE_INDEX = 2
    }
}
