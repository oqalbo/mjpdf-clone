// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.actions

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.Shell
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import java.io.File

object PrintStaging {

    const val STAGING_DIRECTORY = "print"
    const val STAGED_PREFIX = "print-"
    const val STAGED_SUFFIX = ".pdf"
    const val ABSENCE_WINDOW_MS = 1_500L

    val printSpoolerPackages = listOf("com.android.printspooler", "com.google.android.printservice.recommendation")

    fun directory(): File = File(TestEnvironment.targetContext.cacheDir, STAGING_DIRECTORY)

    fun stagedFiles(): List<File> =
        directory().listFiles()
            ?.filter { it.name.startsWith(STAGED_PREFIX) && it.name.endsWith(STAGED_SUFFIX) }
            .orEmpty()
            .sortedBy { it.name }

    fun awaitStagedFileCount(expected: Int, timeoutMs: Long = Await.LONG_TIMEOUT_MS): List<File> =
        Await.untilValue(
            "the print staging directory ${directory().absolutePath} holds exactly $expected files",
            timeoutMs,
            read = { stagedFiles() },
        ) { it.size == expected }

    fun awaitSingleStagedFile(timeoutMs: Long = Await.LONG_TIMEOUT_MS): File =
        awaitStagedFileCount(1, timeoutMs).first()

    fun awaitStagingEmpty(timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        Await.until(
            "the print staging directory ${directory().absolutePath} is emptied",
            timeoutMs,
        ) { stagedFiles().isEmpty() }
    }

    fun assertStagingStaysEmpty(windowMs: Long = ABSENCE_WINDOW_MS) {
        Await.assertThroughout(
            condition = "the print staging directory ${directory().absolutePath} stays empty",
            windowMs = windowMs,
        ) { stagedFiles().isEmpty() }
    }

    fun pageCountOf(file: File, password: String? = null): Int {
        if (password != null) {
            throw AssertionError(
                "PdfRenderer cannot open an encrypted document, so passing a password here can only mean the " +
                    "staged file was expected to still be encrypted. The point of the decrypted staging path is " +
                    "that the staged copy opens without one, so read it with no password and let the failure stand."
            )
        }
        if (!file.isFile) {
            throw ConditionTimeoutException("No staged print file at ${file.absolutePath}")
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer -> renderer.pageCount }
        }
    }

    fun opensWithoutPassword(file: File): Boolean = runCatching { pageCountOf(file) > 0 }.getOrDefault(false)

    fun installedSpoolerPackage(): String? {
        val installed = Shell.executeQuietly("pm list packages")
            .lineSequence()
            .map { it.trim().removePrefix("package:") }
            .filter { it.isNotBlank() }
            .toSet()
        return printSpoolerPackages.firstOrNull { installed.contains(it) }
    }

    fun requireSpoolerInstalled(): String = installedSpoolerPackage()
        ?: throw AssertionError(
            "None of $printSpoolerPackages is installed on this device, so the system print preview cannot open " +
                "and a print test can only assert the staged file. Use an emulator image that carries the print " +
                "spooler, or assert on PrintStaging.awaitSingleStagedFile alone."
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

    fun awaitSpoolerGone(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        val spooler = installedSpoolerPackage() ?: return
        val gone = TestEnvironment.uiDevice.wait(Until.gone(By.pkg(spooler)), timeoutMs)
        if (gone != true) {
            throw ConditionTimeoutException("The print preview from $spooler is still on screen after ${timeoutMs}ms")
        }
    }

    fun dismissSpooler() {
        val spooler = installedSpoolerPackage() ?: return
        repeat(BACK_PRESS_BUDGET) {
            if (TestEnvironment.uiDevice.hasObject(By.pkg(spooler))) {
                TestEnvironment.uiDevice.pressBack()
                TestEnvironment.uiDevice.waitForIdle()
            }
        }
        awaitSpoolerGone()
    }

    fun clear() {
        runCatching { directory().listFiles()?.forEach { it.delete() } }
    }

    private const val BACK_PRESS_BUDGET = 4
}
