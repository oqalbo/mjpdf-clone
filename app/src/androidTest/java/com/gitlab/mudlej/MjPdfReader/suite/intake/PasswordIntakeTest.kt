// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.intake

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
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
class PasswordIntakeTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()

    @Test
    @TestId("INTAKE-25")
    fun aPasswordProtectedDocumentPromptsForItsPassword() {
        val fixture = IntakeFixtures.seeded(IntakeFixtures.PROTECTED)

        ActivityScenario.launch<MainActivity>(IntakeReader.fileViewIntent(fixture)).use {
            awaitPasswordPrompt(Await.LONG_TIMEOUT_MS)

            assertEquals(
                "no page may render before the password is accepted",
                0 as Int?,
                reader.readState().pageCount,
            )

            enterPassword(IntakeFixtures.PROTECTED_PASSWORD, save = false)

            reader.awaitLoaded()
            val state = reader.awaitFullQualityRender()
            assertEquals(
                "page count of protected.pdf",
                IntakeFixtures.PROTECTED_PAGES as Int?,
                state.pageCount,
            )

            val record = IntakeDb.awaitRecordCount(1).single()
            assertNull("an unsaved password must not be written to the record", record.password)
        }
    }

    @Test
    @TestId("INTAKE-26")
    fun aWrongPasswordSaysSoAndAsksAgain() {
        val fixture = IntakeFixtures.seeded(IntakeFixtures.PROTECTED)

        ActivityScenario.launch<MainActivity>(IntakeReader.fileViewIntent(fixture)).use {
            awaitPasswordPrompt(Await.LONG_TIMEOUT_MS)

            enterPassword("wrong", save = false)

            Snackbars.awaitText(IntakeDialogs.string(R.string.wrong_password))
            awaitPasswordPrompt(Await.LONG_TIMEOUT_MS)
            IntakeDb.holdEmpty()
        }
    }

    @Test
    @TestId("INTAKE-27")
    fun aSavedPasswordIsReusedOnTheNextOpen() {
        val fixture = IntakeFixtures.seeded(IntakeFixtures.PROTECTED)

        ActivityScenario.launch<MainActivity>(IntakeReader.fileViewIntent(fixture)).use {
            awaitPasswordPrompt(Await.LONG_TIMEOUT_MS)
            enterPassword(IntakeFixtures.PROTECTED_PASSWORD, save = true)
            reader.awaitLoaded()
            reader.awaitFullQualityRender()

            Await.untilValue(
                "the saved password reaches the record",
                Await.LONG_TIMEOUT_MS,
                read = { IntakeDb.records().singleOrNull()?.password },
            ) { it == IntakeFixtures.PROTECTED_PASSWORD }
        }

        ActivityScenario.launch<MainActivity>(IntakeReader.fileViewIntent(fixture)).use {
            reader.awaitLoaded(Await.LONG_TIMEOUT_MS)
            val state = reader.awaitFullQualityRender()

            assertEquals(
                "page count of protected.pdf on the second open",
                IntakeFixtures.PROTECTED_PAGES as Int?,
                state.pageCount,
            )
            IntakeDialogs.holdAbsent(IntakeDialogs.string(R.string.protected_pdf))
        }
    }

    @Test
    @TestId("INTAKE-28")
    @TestDuration(DurationCategory.NORMAL)
    fun dismissingThePasswordDialogLeavesNoRecord() {
        val fixture = IntakeFixtures.seeded(IntakeFixtures.PROTECTED)

        ActivityScenario.launch<MainActivity>(IntakeReader.fileViewIntent(fixture)).use {
            awaitPasswordPrompt(Await.LONG_TIMEOUT_MS)

            IntakeDialogs.tapOutside()
            assertTrue(
                "the password dialog is set not to cancel on a touch outside",
                IntakeDialogs.isShowing(IntakeDialogs.string(R.string.protected_pdf)),
            )

            TestEnvironment.uiDevice.pressBack()
            IntakeDialogs.awaitGone(IntakeDialogs.string(R.string.protected_pdf))

            IntakeDb.holdEmpty()
            assertEquals(
                "a dismissed prompt leaves the load waiting on a password",
                DocumentLoadState.PASSWORD_REQUIRED,
                reader.readState().loadState,
            )
        }
    }

    private fun awaitPasswordPrompt(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        IntakeDialogs.awaitShowing(IntakeDialogs.string(R.string.protected_pdf), timeoutMs)
        Await.untilNoThrow("the password field is reachable", timeoutMs) {
            onView(withId(R.id.passwordInput)).check(matches(isDisplayed()))
        }
    }

    private fun enterPassword(password: String, save: Boolean) {
        onView(withId(R.id.passwordInput)).perform(replaceText(password), closeSoftKeyboard())
        if (save) {
            onView(withId(R.id.savePassword)).perform(click())
        }
        Dialogs.tapPositive()
    }
}
