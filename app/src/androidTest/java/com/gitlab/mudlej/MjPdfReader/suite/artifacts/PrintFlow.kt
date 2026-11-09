// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.artifacts

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.Shell
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.actions.ConfigurableAction
import java.io.File

object PrintFlow {

    val spoolerPackages = listOf("com.android.printspooler", "com.google.android.printservice.recommendation")

    fun seedAsPrimaryToolbarAction() {
        SettingsSeeder.seed(Preferences.primaryButtonActionKey, ConfigurableAction.PRINT.id)
    }

    fun start() {
        onView(withId(R.id.toolbarPrimaryActionOption)).perform(click())
        TestEnvironment.waitForIdleSync()
    }

    fun startAndAwaitStagedFile(timeoutMs: Long = Await.LONG_TIMEOUT_MS): File {
        val before = AppDirectories.printStagedFiles().map { it.name }.toSet()
        start()
        return Await.untilValue(
            "print staging writes a new file into ${AppDirectories.printStaging.absolutePath}",
            timeoutMs,
            read = { AppDirectories.printStagedFiles().filter { it.name !in before && it.length() > 0L } },
        ) { it.isNotEmpty() }.maxByOrNull { it.lastModified() }!!
    }

    fun installedSpoolerPackage(): String? {
        val installed = Shell.executeQuietly("pm list packages")
            .lineSequence()
            .map { it.trim().removePrefix("package:") }
            .filter { it.isNotBlank() }
            .toSet()
        return spoolerPackages.firstOrNull { installed.contains(it) }
    }

    fun requireSpoolerInstalled(): String = installedSpoolerPackage()
        ?: throw AssertionError(
            "None of $spoolerPackages is installed on this device, so the system print preview cannot open and " +
                "PdfDocumentAdapter.onFinish can never run, which is the thing the staging cleanup assertions " +
                "depend on. Use an emulator image that carries the print spooler."
        )

    fun awaitSpoolerShown(timeoutMs: Long = Await.LONG_TIMEOUT_MS): String {
        val spooler = requireSpoolerInstalled()
        val shown = TestEnvironment.uiDevice.wait(Until.hasObject(By.pkg(spooler)), timeoutMs)
        if (shown != true) {
            throw ConditionTimeoutException(
                "The print preview from $spooler did not appear within ${timeoutMs}ms. PrintController stages a " +
                    "copy first and only calls PrintManager.print once the copy verifies, so an absent preview " +
                    "usually means staging failed and a snackbar is on screen instead."
            )
        }
        return spooler
    }

    fun dismiss() {
        val spooler = installedSpoolerPackage()
        repeat(BACK_BUDGET) {
            if (TestEnvironment.resumedActivityOfType(MainActivity::class.java) != null &&
                (spooler == null || !TestEnvironment.uiDevice.hasObject(By.pkg(spooler)))
            ) {
                return@repeat
            }
            TestEnvironment.uiDevice.pressBack()
            TestEnvironment.uiDevice.waitForIdle()
        }
        Await.until("the reader comes back after the print dialog is dismissed", Await.LONG_TIMEOUT_MS) {
            TestEnvironment.resumedActivityOfType(MainActivity::class.java) != null &&
                (spooler == null || !TestEnvironment.uiDevice.hasObject(By.pkg(spooler)))
        }
    }

    private const val BACK_BUDGET = 6
}
