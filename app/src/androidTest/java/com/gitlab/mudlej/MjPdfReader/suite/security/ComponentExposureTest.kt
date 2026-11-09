// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.security

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.FileDescriptors
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.Shell
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.intake.IntakeDb
import com.gitlab.mudlej.MjPdfReader.suite.robot.DocumentPickerRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.state.DocumentLoadState
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class ComponentExposureTest {
    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()
    private val picker = DocumentPickerRobot()

    private val packageManager: PackageManager get() = TestEnvironment.targetContext.packageManager

    private val appPackage: String get() = TestEnvironment.targetPackage

    @Test
    @TestId("SEC-36")
    fun onlyTheLauncherAliasAndTheReaderAreExternallyLaunchable() {
        val info = packageManager.getPackageInfo(
            appPackage,
            PackageManager.GET_ACTIVITIES or PackageManager.GET_PROVIDERS or
                PackageManager.GET_RECEIVERS or PackageManager.GET_SERVICES or
                PackageManager.MATCH_DISABLED_COMPONENTS,
        )

        val activities = (info.activities ?: emptyArray()).associateBy { it.name }
        assertEquals(
            "exactly the launcher alias and the reader may be exported",
            listOf("$appPackage.Launcher", "$appPackage.ui.reader.MainActivity").sorted(),
            activities.values.filter { it.exported }.map { it.name }.sorted(),
        )

        for (className in LOCKED_DOWN_ACTIVITIES) {
            val activity = activities["$appPackage$className"]
                ?: throw AssertionError(
                    "$className is not declared by the installed package, only ${activities.keys.sorted()} are"
                )
            assertFalse("$appPackage$className must not be exported", activity.exported)
        }

        val fileProvider = (info.providers ?: emptyArray())
            .firstOrNull { it.name == FILE_PROVIDER_CLASS }
            ?: throw AssertionError(
                "the FileProvider is not declared, the package holds " +
                    "${(info.providers ?: emptyArray<ProviderInfo>()).map { it.name }}"
            )
        assertFalse("the FileProvider must not be exported", fileProvider.exported)
        assertTrue("the FileProvider must grant URI permissions", fileProvider.grantUriPermissions)
        assertEquals(
            "the FileProvider authority must be the one file_paths.xml is attached to",
            "$appPackage.fileprovider",
            fileProvider.authority,
        )

        assertEquals(
            "no provider the app itself declares may be exported",
            emptyList<String>(),
            (info.providers ?: emptyArray()).filter { it.exported }.map { it.name },
        )

        val receivers = (info.receivers ?: emptyArray()).associateBy { it.name }
        val exportedReceivers = receivers.values.filter { it.exported }.map { it.name }
        assertEquals(
            "the exported receiver set must be exactly the library receivers this app pulls in",
            LIBRARY_EXPORTED_RECEIVERS.sorted(),
            exportedReceivers.sorted(),
        )
        REQUIRED_RECEIVER_PERMISSIONS.forEach { (className, permission) ->
            assertEquals(
                "$className must remain protected by $permission while exported",
                permission,
                receivers[className]?.permission,
            )
        }

        val services = (info.services ?: emptyArray()).associateBy { it.name }
        val exportedServices = services.values.filter { it.exported }.map { it.name }
        assertEquals(
            "the exported service set must be exactly the library services this app pulls in",
            LIBRARY_EXPORTED_SERVICES.sorted(),
            exportedServices.sorted(),
        )
        REQUIRED_SERVICE_PERMISSIONS.forEach { (className, permission) ->
            assertEquals(
                "$className must remain protected by $permission while exported",
                permission,
                services[className]?.permission,
            )
        }
        assertEquals(
            "the app's own package namespace must contribute no exported receiver and no exported service",
            emptyList<String>(),
            (exportedReceivers + exportedServices).filter { it.startsWith(appPackage) },
        )
    }

    @Test
    @TestId("SEC-37")
    @TestDuration(DurationCategory.LONG)
    fun aNonExportedActivityCannotBeStartedFromOutsideTheApp() {
        assumeTrue(
            "SEC-37 needs a shell that can run am start. Instrumentation shares the app uid, so an in " +
                "process start on a non exported component succeeds and proves nothing.",
            ShellIntent.isAvailable(),
        )
        assertEquals(
            "the captured command must run as the external shell uid",
            "2000",
            Shell.executeWithStderr("id -u").trim(),
        )

        for (className in LOCKED_DOWN_ACTIVITIES) {
            val result = ShellIntent.startComponent("$appPackage$className")
            assertTrue(
                "$appPackage$className must be refused by the package manager as a non-exported component: $result",
                result.refusedAsNotExported,
            )
            TestEnvironment.uiDevice.waitForIdle()
            Await.assertThroughout(
                condition = "$className does not reach the foreground after a refused start",
                windowMs = SHELL_SETTLE_MS,
            ) { TestEnvironment.uiDevice.currentPackageName != appPackage }
        }
    }

    @Test
    @TestId("SEC-38")
    fun aViewIntentWithNoDataAndNoExtrasFallsBackToThePicker() {
        assumeTrue(
            "SEC-38 needs a shell that can run am start, so the launch comes from outside the app uid.",
            ShellIntent.isAvailable(),
        )

        val result = ShellIntent.startViewWithNoData(READER_CLASS)
        assertTrue("the reader is exported, so a shell view start must be accepted: $result", result.succeeded)

        picker.awaitShown(Await.LONG_TIMEOUT_MS)
        IntakeDb.holdEmpty()
        picker.dismissIfShown()
        TestEnvironment.uiDevice.pressBack()
        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
    }

    @Test
    @TestId("SEC-39")
    fun externalIntentsForPrivateNonPdfFilesFailWithoutRendering() {
        assumeTrue(
            "SEC-39 needs a shell that can run am start, so the intent arrives without a grant from a " +
                "different uid.",
            ShellIntent.isAvailable(),
        )

        androidx.test.core.app.ActivityScenario.launch<MainActivity>(
            SecurityReader.fixtureViewIntent(SecurityFixtures.NORMAL)
        ).use {
            reader.awaitReady()
            IntakeDb.awaitRecordCount(1, Await.LONG_TIMEOUT_MS)
        }
        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)

        for (relativePath in PRIVATE_NON_PDF_PATHS) {
            val target = File(FileTree.dataDirectory, relativePath)
            assertTrue("the private non-PDF input must exist", target.isFile)

            val signature = ByteArray(5)
            target.inputStream().use { assertEquals(5, it.read(signature)) }
            assertFalse("this input tests non-PDF rejection", signature.contentEquals("%PDF-".toByteArray(Charsets.US_ASCII)))
            ShellIntent.startView("file://${target.absolutePath}", READER_CLASS)
            Screens.awaitResumed(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
            reader.awaitShown(Await.LONG_TIMEOUT_MS)

            val state = reader.awaitState(
                "the load of ${target.name} reports failed",
                Await.LONG_TIMEOUT_MS,
            ) { it.loadState == DocumentLoadState.FAILED }
            assertEquals(DocumentLoadState.FAILED, state.loadState)
            assertEquals(
                "a non-PDF input must render no pages, observed ${state.pageCount}",
                0 as Int?,
                state.pageCount,
            )
            Snackbars.awaitText(string(R.string.file_opening_error))

            FileDescriptors.awaitNoneFor(target)
            reader.pressBack()
            Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        }

        IntakeDb.holdRecordCount(1)
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    private companion object {
        const val READER_CLASS = ".ui.reader.MainActivity"
        const val FILE_PROVIDER_CLASS = "androidx.core.content.FileProvider"
        const val SHELL_SETTLE_MS = 1_000L

        val LOCKED_DOWN_ACTIVITIES = listOf(
            ".ui.settings.SettingsActivity",
            ".ui.about.AboutActivity",
            ".ui.intro.MainIntroActivity",
            ".ui.home.HomeActivity",
            ".ui.search.SearchActivity",
            ".ui.gotopage.GoToPageActivity",
            ".ui.links.LinksActivity",
            ".ui.tableofcontents.TableOfContentsActivity",
            ".ui.userbookmarks.UserBookmarksActivity",
            ".ui.usernotes.UserNotesActivity",
            ".ui.userhighlights.UserHighlightsActivity",
            ".ui.history.NavigationHistoryActivity",
            ".ui.history.ReadingHistoryActivity",
            ".ui.home.ScanLocationsActivity",
            ".ui.text_mode.TextModeActivity",
            ".ui.about.WhatsNewActivity",
            ".ui.about.AppFeaturesActivity",
            ".ui.about.FeatureTopicActivity",
        )

        val REQUIRED_RECEIVER_PERMISSIONS = mapOf(
            "androidx.work.impl.diagnostics.DiagnosticsReceiver" to "android.permission.DUMP",
            "androidx.profileinstaller.ProfileInstallReceiver" to "android.permission.DUMP",
        )

        val REQUIRED_SERVICE_PERMISSIONS = mapOf(
            "androidx.work.impl.background.systemjob.SystemJobService" to "android.permission.BIND_JOB_SERVICE",
        )

        val LIBRARY_EXPORTED_RECEIVERS = REQUIRED_RECEIVER_PERMISSIONS.keys.toList()

        val LIBRARY_EXPORTED_SERVICES = REQUIRED_SERVICE_PERMISSIONS.keys.toList()

        val PRIVATE_NON_PDF_PATHS = listOf(
            "databases/app-db.db",
            "shared_prefs/com.gitlab.mudlej.MjPdfReader_preferences.xml",
        )
    }
}
