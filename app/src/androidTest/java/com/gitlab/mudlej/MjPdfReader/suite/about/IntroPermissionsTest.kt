// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.about

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.IntroRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.RealOnboarding
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.intro.MainIntroActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import com.gitlab.mudlej.MjPdfReader.suite.history.PhaseRun
import com.gitlab.mudlej.MjPdfReader.suite.rules.FailureArtifactRule
import com.gitlab.mudlej.MjPdfReader.suite.rules.OrientationRule
import com.gitlab.mudlej.MjPdfReader.suite.rules.CrashGateRule
import com.gitlab.mudlej.MjPdfReader.suite.rules.ToastRule
import org.junit.rules.TestRule
import org.junit.rules.RuleChain
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

@LargeTest
@RealOnboarding
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class IntroPermissionsTest {

    @get:Rule
    val rules: TestRule = if (PhaseRun.scenario() == DENIED_SCENARIO) {
        RuleChain.outerRule(OrientationRule())
            .around(FailureArtifactRule())
            .around(CrashGateRule())
            .around(ToastRule())
    } else {
        SuiteRules.standard()
    }

    private val home = HomeRobot()
    private val intro = IntroRobot()

    @Test
    @TestId("ABOUT-17")
    fun theIntroRequestsNoRuntimePermissions() {
        PhaseRun.requirePhase(DENIED_SCENARIO, 1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            assertFalse("all-files access starts denied", Environment.isExternalStorageManager())
        }
        assertEquals(
            "legacy read permission also starts denied",
            PackageManager.PERMISSION_DENIED,
            ContextCompat.checkSelfPermission(TestEnvironment.targetContext, Manifest.permission.READ_EXTERNAL_STORAGE),
        )
        walkIntroWithoutPermissionChanges()
    }

    @Test
    @TestId("ABOUT-17-GRANTED")
    fun theIntroPreservesAlreadyGrantedStorageAccess() {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(TestEnvironment.targetContext, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }
        assertTrue("the granted-access case starts with storage access", granted)
        walkIntroWithoutPermissionChanges()
        assertTheForeignWindowDetectorWorks()
    }

    private fun walkIntroWithoutPermissionChanges() {
        val accessBefore = storageAccessState()

        home.startFromLauncher()
        intro.awaitShown()
        intro.awaitPage(0, IntroRobot.pageTitle(0))
        assertNoForeignWindow("the intro opened")

        val last = IntroRobot.EXPECTED_PAGE_COUNT - 1
        for (position in 1..last) {
            intro.tapNext()
            intro.awaitPage(position, IntroRobot.pageTitle(position))
            assertNoForeignWindow("the intro moved to page $position")
        }

        intro.tapNext()
        Screens.awaitGone(MainIntroActivity::class.java, Await.LONG_TIMEOUT_MS)
        assertNoForeignWindow("the intro finished")

        assertEquals(
            "the intro must leave the storage access state exactly as it found it",
            accessBefore,
            storageAccessState(),
        )

    }

    private fun assertNoForeignWindow(stage: String) {
        assertFalse(
            "a permission dialog was on screen after $stage, the foreground package read " +
                "\"${foregroundPackage()}\"",
            isPermissionControllerShowing(),
        )
        assertEquals(
            "a window from another package was on top after $stage, so something the intro did put it there",
            TestEnvironment.targetPackage,
            foregroundPackage(),
        )
    }

    private fun assertTheForeignWindowDetectorWorks() {
        val context = TestEnvironment.targetContext
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${TestEnvironment.targetPackage}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val handler = context.packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName
            ?: throw AssertionError(
                "no package on this image answers the app details settings intent, so the control that " +
                    "proves the foreign window detector can see anything cannot run"
            )

        context.startActivity(intent)
        Await.untilValue(
            "the system settings window takes the foreground, proving the detector notices a foreign window",
            Await.LONG_TIMEOUT_MS,
            read = { foregroundPackage() },
        ) { it == handler }
        assertTrue(
            "the detector must find a window belonging to \"$handler\" while that window is on top",
            TestEnvironment.uiDevice.hasObject(By.pkg(handler).depth(0)),
        )

        repeat(BACK_PRESS_BUDGET) {
            if (foregroundPackage() != handler) return@repeat
            TestEnvironment.uiDevice.pressBack()
            TestEnvironment.uiDevice.waitForIdle()
        }
        Await.untilValue(
            "the system settings window goes away again",
            Await.LONG_TIMEOUT_MS,
            read = { foregroundPackage() },
        ) { it != handler }
    }

    private fun isPermissionControllerShowing(): Boolean =
        TestEnvironment.uiDevice.hasObject(By.pkg(PERMISSION_CONTROLLER).depth(0))

    private fun foregroundPackage(): String? = TestEnvironment.uiDevice.currentPackageName

    private fun storageAccessState(): String {
        val manager = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()
        val read = ContextCompat.checkSelfPermission(
            TestEnvironment.targetContext,
            Manifest.permission.READ_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
        return "isExternalStorageManager=$manager;readExternalStorageGranted=$read"
    }

    private companion object {
        const val DENIED_SCENARIO = "ABOUT-17-DENIED"

        const val BACK_PRESS_BUDGET = 4

        val PERMISSION_CONTROLLER: Pattern = Pattern.compile(".*\\.permissioncontroller")
    }
}
