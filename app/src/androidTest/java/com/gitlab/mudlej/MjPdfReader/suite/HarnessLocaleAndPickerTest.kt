// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.privacy.PrivacyHome
import com.gitlab.mudlej.MjPdfReader.suite.privacy.PrivacyReader
import com.gitlab.mudlej.MjPdfReader.suite.robot.LocaleRobot
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import java.io.File
import com.gitlab.mudlej.MjPdfReader.suite.robot.DocumentPickerRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.SafPickerRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.LocaleResetRule
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeActivity
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class HarnessLocaleAndPickerTest {

    @get:Rule
    val rules = SuiteRules.standard()

    @get:Rule
    val localeReset = LocaleResetRule()

    private val locale = LocaleRobot()
    private val picker = SafPickerRobot()

    @Test
    @TestId("WAVE0-20")
    @SdkSuppress(minSdkVersion = 33)
    @TestDuration(DurationCategory.SHORT)
    fun theLocaleRobotSetsAndClearsThePerAppLocale() {
        val processBefore = locale.appProcessId()
        assertTrue("the probe needs a running app process to observe", processBefore != null)

        locale.setAppLocaleByShell(LocaleRobot.ARABIC_EGYPT)
        locale.awaitLocaleTagApplied("ar")

        assertTrue(
            "the system must report the per app locale after it is set, read ${locale.currentAppLocales()}",
            locale.currentAppLocales().contains("ar", ignoreCase = true),
        )

        locale.reset()

        val afterReset = locale.currentAppLocales()
        assertFalse(
            "the reset must clear the per app locale, read $afterReset",
            afterReset.contains("ar-EG", ignoreCase = true),
        )
    }

    @Test
    @TestId("WAVE0-21")
    fun theSafPickerRobotFindsTheSystemPickerAndItsControls() {
        TestEnvironment.targetContext.startActivity(
            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )

        picker.awaitShown()
        assertTrue("the document picker must be showing", picker.isShowing())

        picker.navigateToDownloads()
        assertTrue("the picker must still be showing after opening Downloads", picker.isShowing())

        assertTrue(
            "the folder confirmation button must be findable by one of the known labels",
            picker.hasFolderConfirmationButton(),
        )

        picker.cancel()
        assertFalse("the picker must be dismissed after cancelling", picker.isShowing())
    }

    @Test
    @TestId("WAVE0-22")
    fun visibleFileSelectionFailsFastWithoutScrollingWhenTheExactNameIsAbsent() {
        TestEnvironment.targetContext.startActivity(
            Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/pdf")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )

        picker.awaitShown()
        picker.navigateToDownloads()
        val rootBefore = picker.currentRootTitle()
            ?: throw AssertionError("the Downloads picker root has no toolbar title")
        val visibleBefore = picker.visibleTexts()
        val expectedDiagnostic =
            "The picker shows no visible entry named $ABSENT_FILE in root \"$rootBefore\", " +
                "visible entries are $visibleBefore"

        val failure = try {
            picker.chooseVisibleFileNamed(ABSENT_FILE, Await.SHORT_TIMEOUT_MS)
            throw AssertionError("the guaranteed-absent exact picker row unexpectedly opened")
        } catch (expected: ConditionTimeoutException) {
            expected
        }

        assertEquals(expectedDiagnostic, failure.message)
        assertTrue("the picker must remain showing after the bounded miss", picker.isShowing())
        assertEquals("the bounded miss must not navigate away", rootBefore, picker.currentRootTitle())
        assertEquals("the bounded miss must not scroll or replace the visible rows", visibleBefore, picker.visibleTexts())

        picker.cancel()
        assertFalse("the picker must dismiss after the miss probe cancels it", picker.isShowing())
    }

    @Test
    @TestId("WAVE0-25")
    @TestDuration(DurationCategory.SHORT)
    @SdkSuppress(minSdkVersion = 33)
    fun downloadsSelectionDoesNotOpenANestedFileWithTheSameName() {
        val name = "mjpdf-picker-root-match.pdf"
        val rootFile = File(Fixtures.externalRoot, "Download/$name")
        val nestedDirectory = File(Fixtures.externalRoot, "Download/mjpdf-picker-nested-test")
        val nestedFile = File(nestedDirectory, name)
        val home = PrivacyHome()
        val reader = PrivacyReader()
        try {
            nestedDirectory.mkdirs()
            File(Fixtures.externalRoot, "Download/normal.pdf").copyTo(rootFile, overwrite = true)
            File(Fixtures.externalRoot, "Download/hist-beta.pdf").copyTo(nestedFile, overwrite = true)
            nestedFile.setLastModified(System.currentTimeMillis() + 60_000L)
            Fixtures.scanAndWait(rootFile)
            Fixtures.scanAndWait(nestedFile)
            home.launch()
            home.openNormallyFromDownloads(name)
            val opened = reader.awaitReady()
            assertEquals("selection must open the twenty-page root file, not the twelve-page nested file", 20 as Int?, opened.pageCount)
        } finally {
            reader.finishTask()
            home.picker.dismissIfShown()
            Fixtures.delete(rootFile)
            Fixtures.delete(nestedDirectory)
        }
    }

    @Test
    @TestId("WAVE0-24")
    @TestDuration(DurationCategory.LONG)
    @SdkSuppress(minSdkVersion = 33)
    fun downloadsSelectionRefusesASimilarNameWhenTheExactFileIsAbsent() {
        val nearMatch = File(Fixtures.externalRoot, "Download/$ABSENT_FILE.extra.pdf")
        val downloads = DocumentPickerRobot()
        try {
            File(Fixtures.externalRoot, "Download/normal.pdf").copyTo(nearMatch, overwrite = true)
            Fixtures.scanAndWait(nearMatch)
            TestEnvironment.targetContext.startActivity(
                Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/pdf").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            downloads.awaitShown()
            try {
                downloads.pickFromDownloads(ABSENT_FILE, Await.SHORT_TIMEOUT_MS)
                throw AssertionError("a similar name must not be selected as the requested file")
            } catch (_: ConditionTimeoutException) {
                assertTrue("an absent exact file must leave the picker open", downloads.isShown())
            }
        } finally {
            downloads.dismissIfShown()
            Fixtures.delete(nearMatch)
        }
    }

    @Test
    @TestId("WAVE0-23")
    @TestDuration(DurationCategory.SHORT)
    fun aDeeplyNestedPickerLabelClicksItsCardInsteadOfAnOverlappingControl() {
        val cardClicked = AtomicBoolean()
        val overlappingControlClicked = AtomicBoolean()
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val density = activity.resources.displayMetrics.density
                fun dp(value: Int) = (value * density).toInt()
                val root = FrameLayout(activity)
                val card = FrameLayout(activity).apply {
                    setOnClickListener { cardClicked.set(true) }
                }
                root.addView(card, FrameLayout.LayoutParams(dp(280), dp(240)).apply {
                    leftMargin = dp(24)
                    topMargin = dp(100)
                })
                var parent = card
                repeat(6) {
                    val child = FrameLayout(activity)
                    parent.addView(child, ViewGroup.LayoutParams(-1, -1))
                    parent = child
                }
                parent.addView(TextView(activity).apply {
                    text = NESTED_FILE
                    gravity = Gravity.CENTER
                }, FrameLayout.LayoutParams(-1, dp(48), Gravity.BOTTOM))
                root.addView(View(activity).apply {
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    setOnClickListener { overlappingControlClicked.set(true) }
                }, FrameLayout.LayoutParams(dp(280), dp(48)).apply {
                    leftMargin = dp(24)
                    topMargin = dp(292)
                })
                activity.setContentView(root)
            }
            DocumentPickerRobot().select(NESTED_FILE)
            assertTrue("selection must activate the containing file card", cardClicked.get())
            assertFalse("selection must not hit a control covering the filename", overlappingControlClicked.get())
        }
    }

    private companion object {
        const val ABSENT_FILE = "mjpdf-guaranteed-absent-picker-probe.pdf"
        const val NESTED_FILE = "mjpdf-nested-picker-probe.pdf"
    }
}
