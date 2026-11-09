// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.robot

import com.gitlab.mudlej.MjPdfReader.suite.core.SuiteTiming
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import java.util.regex.Pattern

class DocumentPickerRobot {

    private val device get() = TestEnvironment.uiDevice

    fun isShown(): Boolean = device.hasObject(By.pkg(DOCUMENTS_UI).depth(0))

    fun awaitShown(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): DocumentPickerRobot {
        val appeared = device.wait(Until.hasObject(By.pkg(DOCUMENTS_UI).depth(0)), timeoutMs)
        if (appeared != true) {
            throw ConditionTimeoutException(
                "Timed out after ${timeoutMs}ms waiting for the system document picker, " +
                    "the foreground package is ${device.currentPackageName}"
            )
        }
        SuiteTiming.measure("picker.idle") { device.waitForIdle() }
        return this
    }

    fun awaitGone(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): DocumentPickerRobot {
        Await.until("the system document picker is gone", timeoutMs) { !isShown() }
        return this
    }

    fun cancel(): DocumentPickerRobot {
        repeat(MAX_BACK_PRESSES) {
            if (!isShown()) return this
            device.pressBack()
            SuiteTiming.measure("picker.idle") { device.waitForIdle() }
        }
        return this
    }

    fun dismissIfShown() {
        if (isShown()) cancel()
    }

    fun isInDownloads(): Boolean = IN_DOWNLOADS_HEADERS.any { header ->
        listOf(By.text(header), By.desc(header)).any { selector ->
            device.findObject(selector)?.visibleBounds?.isEmpty == false
        }
    }

    fun pickFromDownloads(fileName: String, timeoutMs: Long = Await.LONG_TIMEOUT_MS): DocumentPickerRobot =
        SuiteTiming.measure("picker.select") {
            SafPickerRobot().navigateToDownloads().showListView()
                .chooseFileNamedInCurrentFolder(fileName, timeoutMs)
            this
        }

    fun openDownloads(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): DocumentPickerRobot {
        awaitShown()
        Await.until("the picker is showing the Downloads root", timeoutMs, pollMs = POLL_MS) {
            if (isInDownloads()) return@until true
            enterDownloads()
            isInDownloads()
        }
        return this
    }

    fun select(fileName: String, timeoutMs: Long = Await.LONG_TIMEOUT_MS): DocumentPickerRobot {
        val selectors = entrySelectors(fileName)
        Await.until("the picker entry for $fileName is tapped", timeoutMs, pollMs = POLL_MS) {
            if (tapIfPresent(selectors)) return@until true
            scrollToward(fileName)
            tapIfPresent(selectors)
        }
        return this
    }

    private fun entrySelectors(fileName: String): List<BySelector> {
        val stem = fileName.substringBeforeLast('.')
        return listOf(
            By.text(fileName),
            By.text(stem),
        )
    }

    private fun scrollToward(fileName: String) = SuiteTiming.measure("picker.scroll") {
        runCatching {
            val list = UiScrollable(UiSelector().scrollable(true))
            list.setMaxSearchSwipes(MAX_SEARCH_SWIPES)
            list.scrollIntoView(UiSelector().text(fileName))
        }
        SuiteTiming.measure("picker.idle") { device.waitForIdle() }
    }

    private fun openRootsDrawer(): Boolean {
        if (device.hasObject(By.text(ROOTS_HEADER))) return true
        tapIfPresent(listOf(By.desc(SHOW_ROOTS_DESC), By.desc(NAVIGATE_UP_DESC)))
        return device.wait(Until.hasObject(By.text(ROOTS_HEADER)), DRAWER_TIMEOUT_MS) == true
    }

    private fun enterDownloads() {
        if (!openRootsDrawer()) return
        tapIfPresent(listOf(By.text(DOWNLOADS_LABEL), By.desc(DOWNLOADS_LABEL)))
        Await.until("the picker reaches Downloads", DRAWER_TIMEOUT_MS) { isInDownloads() }
    }

    private fun findAny(selectors: List<BySelector>): UiObject2? =
        selectors.firstNotNullOfOrNull { device.findObject(it) }

    private fun tapIfPresent(selectors: List<BySelector>): Boolean {
        val target = findAny(selectors) ?: return false
        return try {
            if (!PickerClicks.click(target)) return false
            SuiteTiming.measure("picker.idle") { device.waitForIdle() }
            true
        } catch (stale: StaleObjectException) {
            false
        }
    }

    companion object {
        val DOCUMENTS_UI: Pattern = Pattern.compile(".*\\.documentsui")
        const val DOWNLOADS_LABEL = "Downloads"
        val IN_DOWNLOADS_HEADERS = listOf("Files in Downloads", "Downloads")
        const val ROOTS_HEADER = "Open from"
        const val SHOW_ROOTS_DESC = "Show roots"
        const val NAVIGATE_UP_DESC = "Navigate up"
        const val MAX_SEARCH_SWIPES = 40
        const val POLL_MS = 250L
        const val MAX_BACK_PRESSES = 6
        const val DRAWER_TIMEOUT_MS = 3_000L
    }
}
