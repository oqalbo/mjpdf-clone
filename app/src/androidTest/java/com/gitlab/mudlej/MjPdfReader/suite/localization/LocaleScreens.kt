// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.localization

import android.app.Activity
import android.graphics.PointF
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import androidx.viewpager2.widget.ViewPager2
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationDialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TouchActions
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomePage
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.ui.about.WhatsNewActivity
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeTab
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity

object LocaleActions {

    fun openActionsDialog() {
        tapToolbarItem(R.id.readerActionsOption)
        Dialogs.awaitActionsDialog(Await.LONG_TIMEOUT_MS)
    }

    fun tapDialogEntry(label: String) {
        onView(withContentDescription(label)).inRoot(isDialog())
            .perform(ViewActions.scrollTo(), TouchActions.click())
        TestEnvironment.waitForIdleSync()
    }

    fun tapToolbarItem(viewId: Int) {
        onView(withId(viewId)).perform(TouchActions.click())
        TestEnvironment.waitForIdleSync()
    }

    fun toolbarItemLabel(viewId: Int): String = Await.untilNotNull(
        "the reader toolbar item ${LocalizedStrings.nameOf(viewId)} carries a label",
        Await.LONG_TIMEOUT_MS,
    ) {
        TestEnvironment.onMainThread {
            TestEnvironment.resumedActivityOfType(MainActivity::class.java)
                ?.window?.decorView?.findViewById<View>(viewId)
                ?.takeIf { it.isShown }
                ?.contentDescription?.toString()
                ?.takeIf { it.isNotBlank() }
        }
    }
}

object LocaleHome {

    fun awaitLibrary(home: HomeRobot, expectedItems: Int) {
        home.library().awaitItemCount(expectedItems, Await.LONG_TIMEOUT_MS)
    }

    fun selectItems(home: HomeRobot, titles: List<String>) {
        require(titles.isNotEmpty()) { "At least one locale record is required for selection" }
        awaitLibrary(home, titles.size)
        val folders = openRecordsFolder(home, titles.size)
        folders.cell(titles.first()).performLongClickForSetupAndAwaitChecked()
        awaitSelectionCount(home, 1)
        titles.drop(1).forEachIndexed { index, title ->
            folders.cell(title).performClickForSetupAndAwaitChecked()
            awaitSelectionCount(home, index + 2)
        }
    }

    fun tapDelete(expectedBody: String) {
        tapSelectionAction(R.id.deleteBatchOption, expectedBody)
    }

    private fun tapSelectionAction(viewId: Int, expectedDialogText: String) {
        val accepted = TestEnvironment.onMainThread {
            val activity = TestEnvironment.resumedActivity()
                ?: throw ConditionTimeoutException("No activity is resumed to act on")
            val toolbar = activity.findViewById<Toolbar>(R.id.selectionToolbar)
                ?.takeIf { it.isShown }
                ?: throw ConditionTimeoutException("The selection toolbar is not shown")
            val item = toolbar.menu.findItem(viewId)
                ?: throw ConditionTimeoutException("The selection toolbar carries no action $viewId")
            if (!item.isVisible) {
                throw ConditionTimeoutException("The selection toolbar action $viewId is not visible")
            }
            if (!item.isEnabled) {
                throw ConditionTimeoutException("The selection toolbar action $viewId is not enabled")
            }
            toolbar.menu.performIdentifierAction(viewId, 0)
        }
        if (!accepted) {
            throw ConditionTimeoutException("The selection toolbar did not accept action $viewId")
        }
        Await.until(
            "the selection action $viewId opens a confirmation reading \"$expectedDialogText\""
        ) {
            TestEnvironment.instrumentation.uiAutomation.rootInActiveWindow
                ?.findAccessibilityNodeInfosByText(expectedDialogText)
                ?.any { it.isVisibleToUser && it.text?.toString() == expectedDialogText } == true
        }
    }

    private fun awaitSelectionCount(home: HomeRobot, count: Int) {
        val expected = TestEnvironment.appString(R.string.home_selected_count, count)
        home.awaitSelectionTitle(expected, Await.LONG_TIMEOUT_MS)
    }

    private fun openRecordsFolder(home: HomeRobot, expectedItems: Int): HomePage {
        TestEnvironment.onMainThread {
            val activity = TestEnvironment.resumedActivity()
                ?: throw ConditionTimeoutException("No activity is resumed to open locale records")
            val pager = activity.findViewById<ViewPager2>(R.id.homePager)
                ?: throw ConditionTimeoutException("The resumed activity carries no home pager")
            pager.setCurrentItem(HomeTab.FOLDERS.ordinal, false)
        }
        home.awaitPagerItem(HomeTab.FOLDERS, Await.LONG_TIMEOUT_MS)
        home.awaitState("the home hook reports the folders tab", Await.LONG_TIMEOUT_MS) {
            it.tab == HomeTab.FOLDERS
        }
        return home.folders().also { folders ->
            Await.until("the folders tab lists \"$RECORDS_FOLDER\"", Await.LONG_TIMEOUT_MS) {
                RECORDS_FOLDER in folders.folderNames()
            }
            folders.activateFolderForSetup(
                RECORDS_FOLDER,
                expectedItems,
                Await.LONG_TIMEOUT_MS,
            )
        }
    }

    private const val RECORDS_FOLDER = "locale-records"
}

object LocaleSettings {

    fun openPage(title: String) {
        tapText(title)
        TestEnvironment.waitForIdleSync()
    }

    fun openRow(title: String) {
        tapText(title)
        TestEnvironment.waitForIdleSync()
    }

    fun summaryUnder(title: String): String {
        scrollTo(title)
        val row = TestEnvironment.uiDevice.wait(Until.findObject(By.text(title)), Await.LONG_TIMEOUT_MS)
            ?: throw ConditionTimeoutException("No settings row titled \"$title\" is on screen")
        val parent = row.parent ?: throw ConditionTimeoutException("The row \"$title\" has no container to read")
        return parent.children
            .mapNotNull { it.text }
            .firstOrNull { it != title && it.isNotBlank() }
            ?: throw ConditionTimeoutException("The row \"$title\" shows no summary line beneath it")
    }

    fun scrollTo(title: String) {
        val scrollable = TestEnvironment.uiDevice.findObject(By.scrollable(true)) ?: return
        repeat(SCROLL_STEPS) {
            if (TestEnvironment.uiDevice.hasObject(By.text(title))) return
            if (!scrollable.scroll(androidx.test.uiautomator.Direction.DOWN, SCROLL_FRACTION)) return
        }
    }

    private fun tapText(title: String) {
        scrollTo(title)
        val target = TestEnvironment.uiDevice.wait(Until.findObject(By.text(title)), Await.LONG_TIMEOUT_MS)
            ?: throw ConditionTimeoutException(
                "No settings entry reading \"$title\" is on screen. Visible text was " +
                    TestEnvironment.uiDevice.findObjects(By.pkg(TestEnvironment.targetPackage)).mapNotNull { it.text }
            )
        target.click()
    }

    private const val SCROLL_STEPS = 10
    private const val SCROLL_FRACTION = 0.8f
}

object LocaleGoToPage {

    fun open() {
        LocaleActions.openActionsDialog()
        LocaleActions.tapDialogEntry(AnnotationDialogs.string(R.string.go_to_page))
        AnnotationDialogs.awaitTitle(AnnotationDialogs.string(R.string.go_to_page))
    }

    fun hint(): String = AnnotationDialogs.capture(withId(R.id.inputLayout)) { view ->
        (view as com.google.android.material.textfield.TextInputLayout).hint?.toString().orEmpty()
    }

    fun fieldText(): String = AnnotationDialogs.capture(withId(R.id.editText)) { view ->
        (view as TextView).text.toString()
    }

    fun type(text: String) {
        onView(withId(R.id.editText)).inRoot(isDialog())
            .perform(ViewActions.replaceText(text), ViewActions.closeSoftKeyboard())
        TestEnvironment.waitForIdleSync()
    }

    fun pasteIntoField(text: String) {
        val field = AnnotationDialogs.capture(withId(R.id.editText)) { view -> view as android.widget.EditText }
        TestEnvironment.onMainThread {
            val editable = field.text
            editable.replace(0, editable.length, text)
        }
        TestEnvironment.waitForIdleSync()
    }

    fun isGoToEnabled(): Boolean = AnnotationDialogs.isPositiveEnabled()

    fun awaitGoToEnabled(
        enabled: Boolean,
        timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS,
    ) {
        AnnotationDialogs.awaitPositiveEnabled(enabled, timeoutMs)
    }

    fun tapGo() = AnnotationDialogs.pressPositive()

    fun cancel() = AnnotationDialogs.pressNegative()
}

object LocaleAbout {

    fun launchWhatsNew(): ActivityScenario<WhatsNewActivity> {
        val scenario = ActivityScenario.launch(WhatsNewActivity::class.java)
        Screens.awaitResumed(WhatsNewActivity::class.java, Await.LONG_TIMEOUT_MS)
        return scenario
    }

    fun versionChipText(): String = TestEnvironment.onMainThread {
        val activity = TestEnvironment.resumedActivityOfType(WhatsNewActivity::class.java)
            ?: throw ConditionTimeoutException("The What's New screen is not resumed")
        activity.findViewById<TextView>(R.id.versionChip)?.text?.toString()
            ?: throw ConditionTimeoutException("The What's New screen carries no version chip")
    }
}

internal fun centreOf(view: View): PointF {
    val location = IntArray(2)
    view.getLocationOnScreen(location)
    if (view.width <= 0 || view.height <= 0) {
        throw ConditionTimeoutException("A view has no size on screen, so it cannot be tapped")
    }
    return PointF(location[0] + view.width / 2f, location[1] + view.height / 2f)
}

internal fun resumedActivityOrThrow(type: Class<out Activity>): Activity =
    TestEnvironment.resumedActivityOfType(type)
        ?: throw ConditionTimeoutException("${type.simpleName} is not resumed")
