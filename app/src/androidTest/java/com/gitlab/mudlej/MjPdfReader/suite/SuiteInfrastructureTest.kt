// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.widget.Toast
import android.widget.EditText
import android.widget.LinearLayout
import android.content.Intent
import android.content.ComponentName
import android.net.Uri
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.runner.intent.IntentStubberRegistry

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.BuildConfig
import com.gitlab.mudlej.MjPdfReader.core.ui.toast
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.HeapSampler
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.core.Rotation
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.Shell
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Toasts
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.suite.text.TextNotices
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.DisplaySizeRule
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.rules.IntentStubs
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runner.Description
import org.junit.runners.model.Statement

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class SuiteInfrastructureTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val home = HomeRobot()

    @Test
    @TestId("INFRA-09")
    @TestDuration(DurationCategory.NORMAL)
    fun temporaryDisplayChangesRestoreTheIncomingOverrides() {
        val size = Shell.execute("wm size").trim()
        val density = Shell.execute("wm density").trim()
        val restoreSize = Regex("(?m)^Override size:\\s*(\\S+)").find(size)?.groupValues?.get(1) ?: "reset"
        val restoreDensity = Regex("(?m)^Override density:\\s*(\\S+)").find(density)?.groupValues?.get(1) ?: "reset"
        try {
            val temporary = DisplaySizeRule()
            temporary.apply(object : Statement() {
                override fun evaluate() {
                    temporary.set(320, 640, 120)
                }
            }, Description.createTestDescription(javaClass, "temporaryDisplay")).evaluate()
            assertEquals("a temporary size must restore the incoming override", size, Shell.execute("wm size").trim())
            assertEquals("a temporary density must restore the incoming override", density, Shell.execute("wm density").trim())
        } finally {
            Shell.execute("wm size $restoreSize")
            Shell.execute("wm density $restoreDensity")
        }
    }

    @Test
    @TestId("INFRA-08")
    fun outgoingIntentStubsLeaveTestHarnessActivitiesUnstubbed() {
        Intents.init()
        try {
            IntentStubs.stubEveryOutgoingIntent()
            TestEnvironment.onMainThread {
                val stubber = IntentStubberRegistry.getInstance()
                val cleanup = Intent(Intent.ACTION_MAIN).setComponent(
                    ComponentName(TestEnvironment.testContext.packageName,
                        "androidx.test.core.app.InstrumentationActivityInvoker\$EmptyActivity")
                )
                assertNull("ActivityScenario cleanup must reach its real helper activity", stubber.getActivityResultForIntent(cleanup))
                assertNull("app navigation must remain real", stubber.getActivityResultForIntent(Intent(TestEnvironment.targetContext, HomeActivity::class.java)))
                assertNotNull("external web intents must still be intercepted", stubber.getActivityResultForIntent(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.invalid/intent-stub-control"))))
            }
        } finally {
            Intents.release()
        }
    }

    @Test
    @TestId("INFRA-07")
    fun dialogQueriesObserveShownAndDismissedAppWindows() {
        ActivityScenario.launch<HomeActivity>(Intent(TestEnvironment.targetContext, HomeActivity::class.java)).use {
            home.awaitShown()
            assertFalse(Dialogs.hasDialogTitled(R.string.save_highlights))
            assertFalse(Dialogs.hasViewOnScreen(R.id.editText))
            val dialog = TestEnvironment.onMainThread {
                val activity = TestEnvironment.resumedActivityOfType(HomeActivity::class.java)!!
                MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.save_highlights)
                    .setView(LinearLayout(activity).apply {
                        contentDescription = "Harness action tile"
                        addView(EditText(activity).apply { id = R.id.editText })
                    })
                    .setPositiveButton(android.R.string.ok) { _, _ -> }
                    .create().apply {
                        window!!.addFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                        show()
                    }
            }
            try {
                Dialogs.awaitDialogTitled(R.string.save_highlights)
                Dialogs.awaitViewOnScreen(R.id.editText)
                assertTrue("the robot sees a visible dialog view while the activity holds focus", home.isVisible(R.id.editText))
                assertFalse("the robot does not invent a missing view", home.isVisible(R.id.scanLocationsRow))
                assertTrue(Dialogs.isAnyShowing())
                assertTrue(Dialogs.hasViewOnScreen(android.R.id.button1))
                assertTrue(Dialogs.hasAnyActionTile())
                assertTrue(Dialogs.hasActionTile("Harness action tile"))
                assertFalse(Dialogs.hasActionTile("A different action tile"))
                assertFalse(Dialogs.hasDialogTitled(R.string.go_to_page))
            } finally {
                TestEnvironment.onMainThread { dialog.dismiss() }
            }
            Dialogs.awaitDialogTitleGone(R.string.save_highlights)
            Dialogs.awaitViewGone(R.id.editText)
            assertFalse("the robot no longer sees a dismissed dialog view", home.isVisible(R.id.editText))
            assertFalse(Dialogs.isAnyShowing())
            assertFalse(Dialogs.hasActionTile("Harness action tile"))
        }
    }

    @Test
    @TestId("INFRA-06")
    @TestDuration(DurationCategory.NORMAL)
    fun rotationResetRestoresAndKeepsTheNaturalOrientation() {
        ActivityScenario.launch<HomeActivity>(Intent(TestEnvironment.targetContext, HomeActivity::class.java)).use {
            home.awaitShown()
            TestEnvironment.uiDevice.setOrientationLeft()
            Await.until("Home has completed its landscape layout") {
                TestEnvironment.onMainThread {
                    val activity = TestEnvironment.resumedActivityOfType(HomeActivity::class.java)
                    activity?.resources?.configuration?.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                        activity.window.decorView.width > activity.window.decorView.height
                }
            }
            Rotation.reset()
            awaitNaturalHomeOrientation()
            assertNaturalHomeOrientation()
        }
        ActivityScenario.launch<HomeActivity>(Intent(TestEnvironment.targetContext, HomeActivity::class.java)).use {
            home.awaitShown()
            awaitNaturalHomeOrientation()
            assertNaturalHomeOrientation()
        }
    }

    private fun awaitNaturalHomeOrientation() {
        Await.until("Home has committed its natural layout") {
            TestEnvironment.onMainThread {
                val activity = TestEnvironment.resumedActivityOfType(HomeActivity::class.java)
                activity != null && activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT &&
                    activity.window.decorView.height > activity.window.decorView.width &&
                    !activity.window.decorView.isLayoutRequested
            }
        }
    }

    private fun assertNaturalHomeOrientation() = TestEnvironment.onMainThread {
        val display = TestEnvironment.targetContext.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val activity = TestEnvironment.resumedActivityOfType(HomeActivity::class.java)
            ?: throw AssertionError("Home must remain resumed after the reset")
        assertEquals(Surface.ROTATION_0, display.rotation)
        assertEquals(Configuration.ORIENTATION_PORTRAIT, activity.resources.configuration.orientation)
        assertTrue("the restored window must have portrait bounds", activity.window.decorView.height > activity.window.decorView.width)
    }

    @Test
    @TestId("INFRA-05")
    fun resourceBudgetsIncludeLiveNativeBitmapMemory() {
        val bitmap = android.graphics.Bitmap.createBitmap(4096, 4096, android.graphics.Bitmap.Config.ARGB_8888)
        try {
            val nativeBytes = bitmap.allocationByteCount.toLong()
            val runtime = Runtime.getRuntime()
            val managedBytes = runtime.totalMemory() - runtime.freeMemory()
            assertTrue("the control allocation must exceed managed heap usage", nativeBytes > managedBytes)
            val sampled = HeapSampler.inUseBytes()
            assertTrue("the resource sampler must include the live $nativeBytes-byte native bitmap, sampled $sampled", sampled >= nativeBytes)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    @TestId("INFRA-04")
    fun textNoticeRecordingDetectsToastsFromItsOwnWindow() {
        val earlier = "earlier toast outside notice window"
        TestEnvironment.onMainThread { toast(TestEnvironment.targetContext, earlier, Toast.LENGTH_SHORT) }
        val notices = TextNotices.record()
        try {
            notices.assertNeverSeen(earlier)
            val forbidden = "forbidden toast inside notice window"
            TestEnvironment.onMainThread { toast(TestEnvironment.targetContext, forbidden, Toast.LENGTH_SHORT) }
            val failure = runCatching { notices.assertNeverSeen(forbidden) }.exceptionOrNull()
            assertTrue("toast failures must be detected without a snackbar", failure is AssertionError)
            assertTrue(failure?.message?.contains(forbidden) == true)
        } finally {
            notices.stop()
        }
    }

    @Test
    @TestId("INFRA-03")
    fun invariantChecksRejectAnObservedViolationWithoutRetrying() {
        for (violationAt in listOf(1, 2)) {
            var samples = 0
            val failure = runCatching {
                Records.assertHolds("probe invariant", windowMs = 150L) {
                    ++samples != violationAt
                }
            }.exceptionOrNull()
            assertTrue("an observed invariant violation must fail", failure is AssertionError)
            assertEquals("the failing sample must not be retried", violationAt, samples)
        }
        var sampled = false
        val immediateFailure = runCatching {
            Records.assertHolds("zero-window probe", windowMs = 0L) {
                sampled = true
                false
            }
        }.exceptionOrNull()
        assertTrue("a zero-window assertion still checks the current state", sampled)
        assertTrue(immediateFailure is AssertionError)

        Records.assertHolds("valid invariant", windowMs = 100L) { true }
        var settlingSamples = 0
        Await.untilTrueContinuously("settling probe", stableForMs = 100L) {
            ++settlingSamples > 1
        }
        assertTrue("settling waits may retry an unsettled state", settlingSamples > 1)
    }

    @Test
    @TestId("INFRA-01")
    fun theDebugAppUnderTestLaunchesHome() {
        assertEquals(
            "The instrumentation is attached to the wrong package",
            BuildConfig.APPLICATION_ID,
            TestEnvironment.targetPackage,
        )
        assertTrue("The suite must run against a debug build", BuildConfig.DEBUG)

        assertFalse(
            "The onboarding suppression rule did not run before the test",
            Preferences(SettingsSeeder.preferences).getFirstInstall(),
        )

        home.launchFromLauncher()
        assertTrue("the direct Home visibility probe must see the launched Home pager", home.isShown())
        home.assertShown()

        val forbiddenToast = "INFRA-01 forbidden toast probe"
        TestEnvironment.onMainThread {
            toast(TestEnvironment.targetContext, forbiddenToast, Toast.LENGTH_SHORT)
        }
        val failure = runCatching { Toasts.assertNoneWithin(forbiddenToast, 0L) }.exceptionOrNull()
        assertTrue("the zero-window absence probe must catch a recorded toast", failure is AssertionError)
        assertTrue(
            "the toast absence failure must identify the forbidden literal",
            failure?.message?.contains(forbiddenToast) == true,
        )
    }

    @Test
    @TestId("INFRA-02")
    fun theFixturePusherOwnsAPerTestDirectory() {
        val fixture = rules.testDirectory.fileNamed("infra-probe.pdf")
        Fixtures.pushBytes("%PDF-1.4 probe".toByteArray(), fixture)

        assertTrue("The pushed fixture is not on shared storage", fixture.exists())
        assertEquals(
            "The per test directory is not under the shared storage contract path",
            "${Fixtures.perTestRoot.absolutePath}/INFRA-02",
            fixture.parentFile?.absolutePath,
        )

        val stamped = 1_600_000_000_000L
        Fixtures.setLastModified(fixture, stamped)
        assertEquals("The fixture modification time was not applied", stamped / 1000, fixture.lastModified() / 1000)

        assertTrue(
            "The shell helper could not read the mode of the pushed fixture",
            Fixtures.readMode(fixture).matches(Regex("[0-7]{3,4}")),
        )

        val scanned = Fixtures.scanAndWait(fixture)
        assertTrue("The media scan did not report the pushed path", scanned.containsKey(fixture.absolutePath))
        assertNotNull("The media scan produced no row for the pushed fixture", scanned[fixture.absolutePath])
    }
}
