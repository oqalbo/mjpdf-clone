// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.text

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.AppLogcat
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.ReaderProbe
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.resilience.TextModeProbe
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.text_mode.TextModeActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class TextModeEntryTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()
    private val text = TextModeRobot()

    @Test
    @TestId("TXT-01")
    fun textModeStartsOnTheReadersCurrentPage() {
        ActivityScenario.launch<MainActivity>(
            ReaderRobot.viewIntent(TextFixtures.uri(TextFixtures.NORMAL))
        ).use {
            text.awaitReaderSettled()
            text.readerGoToPage(5)
            reader.awaitPageIndex(4)

            text.openFromReaderMenu()
            val snapshot = text.awaitPageText(4)

            assertTrue(
                "UIAutomator must see the text mode page list",
                TestEnvironment.uiDevice.hasObject(
                    By.res(TestEnvironment.targetPackage, "textPagesRecyclerView")
                ),
            )
            text.awaitPageCounter(text.counterFor(5, TextFixtures.NORMAL_PAGES))
            assertEquals(
                "the first visible text mode item is the page the reader was on",
                text.labelFor(5),
                text.pageLabel(text.firstVisiblePosition()),
            )
            val page = snapshot.pageText!!.toString()
            assertTrue("page 5 must carry text in the reflowed view", page.isNotBlank())
            CapturedReflow.compare(
                "NORMAL_PAGE_5_PREFIX",
                CapturedReflow.NORMAL_PAGE_5_PREFIX,
                page.take(CapturedReflow.PREFIX_LENGTH),
            )

            text.finish()
        }
    }

    @Test
    @TestId("TXT-02")
    @TestDuration(DurationCategory.SHORT)
    fun openInTextModeByDefaultOpensTextModeAfterLoad() {
        SettingsSeeder.seed(Preferences.defaultTextModeKey, true)

        ActivityScenario.launch<MainActivity>(
            ReaderRobot.viewIntent(TextFixtures.uri(TextFixtures.NORMAL))
        ).use {
            text.awaitShown()
            text.awaitPageText(0)

            Screens.assertTopIs(TextModeActivity::class.java)
            text.awaitPageCounter(text.counterFor(1, TextFixtures.NORMAL_PAGES))

            TestEnvironment.uiDevice.pressBack()
            Screens.awaitTopIs(MainActivity::class.java)
            assertFalse(
                "the text mode page list must be gone once the reader is back",
                TestEnvironment.uiDevice.hasObject(
                    By.res(TestEnvironment.targetPackage, "textPagesRecyclerView")
                ),
            )
        }
    }

    @Test
    @TestId("TXT-03")
    fun backToPdfReturnsTheReaderToTheTextModePage() {
        ActivityScenario.launch<MainActivity>(
            ReaderRobot.viewIntent(TextFixtures.uri(TextFixtures.NORMAL))
        ).use {
            text.awaitReaderSettled()
            val startPage = reader.readState().page ?: 0

            text.openFromReaderMenu()
            text.awaitPageText(0)
            text.goToPage(7)
            text.awaitPageCounter(text.counterFor(7, TextFixtures.NORMAL_PAGES))

            text.tapBackToPdf()
            text.awaitFinished()

            reader.awaitPageIndex(6)
            assertEquals("the reader follows text mode to page seven", 6 as Int?, reader.readState().page)
            Await.untilValue(
                "the reader back stack records the page text mode was entered from",
                read = { text.readerBackStackPages() },
            ) { it.contains(startPage) }
        }
    }

    @Test
    @TestId("TXT-04")
    fun systemBackReturnsTheReaderToTheTextModePage() {
        ActivityScenario.launch<MainActivity>(
            ReaderRobot.viewIntent(TextFixtures.uri(TextFixtures.NORMAL))
        ).use {
            text.awaitReaderSettled()
            val hash = reader.awaitFileHashResolved()

            text.openFromReaderMenu()
            text.awaitPageText(0)
            text.goToPage(10)
            text.awaitPageCounter(text.counterFor(10, TextFixtures.NORMAL_PAGES))

            TestEnvironment.uiDevice.pressBack()
            text.awaitFinished()

            reader.awaitPageIndex(9)
            assertEquals("the reader follows text mode to page ten", 9 as Int?, reader.readState().page)
            assertEquals(
                "the stored page index after leaving text mode",
                9,
                Records.awaitStoredPage(hash, 9).pageNumber,
            )
        }
    }

    @Test
    @TestId("TXT-05")
    @TestDuration(DurationCategory.SHORT)
    fun aCorruptDocumentExitsTextModeWithoutMovingTheReader() {
        ActivityScenario.launch<MainActivity>(
            ReaderRobot.viewIntent(TextFixtures.uri(TextFixtures.NORMAL))
        ).use {
            text.awaitReaderSettled()
            val pageBefore = reader.readState().page

            val failureText = text.string(R.string.failed_to_extract_text)
            val missing = File(TestEnvironment.targetContext.cacheDir, "txt05-missing.pdf").also {
                it.delete()
            }
            val noticeSince = AppLogcat.since()
            ReaderProbe.onReader { activity ->
                activity.startActivity(TextModeProbe.intentFor(missing))
            }
            Await.until("the failed extractor open tells the user why text mode closed") {
                AppLogcat.appLinesSince(noticeSince).any {
                    it.contains("MjPdfToast") && it.contains(failureText)
                }
            }

            text.awaitFinished()
            Screens.awaitTopIs(MainActivity::class.java)
            assertEquals(
                "a failed extraction must leave the reader on the page it was on",
                pageBefore,
                reader.readState().page,
            )
            assertTrue(
                "a cancelled text mode result must not push a reader history entry",
                text.readerBackStackPages().isEmpty(),
            )
        }
    }

    @Test
    @TestId("TXT-52")
    @TestDuration(DurationCategory.SHORT)
    fun aPasswordProtectedDocumentOpensInTextMode() {
        ActivityScenario.launch<MainActivity>(
            ReaderRobot.viewIntent(TextFixtures.uri(TextFixtures.PROTECTED))
        ).use {
            unlock(TextFixtures.PROTECTED_PASSWORD)
            reader.awaitLoaded()
            reader.awaitRenderIdle()

            val notices = TextNotices.record()
            try {
                text.openFromReaderMenu()
                val snapshot = text.awaitPageText(0)

                assertTrue(
                    "page one of the protected document must carry text",
                    !snapshot.pageText.isNullOrBlank(),
                )
                CapturedReflow.compare(
                    "PROTECTED_PAGE_1_DEFAULT",
                    CapturedReflow.PROTECTED_PAGE_1_DEFAULT,
                    snapshot.pageText,
                )
                text.awaitPageCounter(text.counterFor(1, TextFixtures.PROTECTED_PAGES))
                notices.assertNeverSeen(text.string(R.string.failed_to_extract_text))
            } finally {
                notices.stop()
            }

            text.finish()
        }
    }

    private fun unlock(password: String) {
        Await.untilNoThrow("the password prompt is reachable", Await.LONG_TIMEOUT_MS) {
            onView(withId(R.id.passwordInput)).check(matches(isDisplayed()))
        }
        onView(withId(R.id.passwordInput)).perform(replaceText(password), closeSoftKeyboard())
        Dialogs.tapPositive()
    }
}
