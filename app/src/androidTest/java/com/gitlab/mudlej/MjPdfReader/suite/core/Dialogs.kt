// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.core

import android.view.View
import android.view.ViewGroup
import android.graphics.Rect
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.platform.view.inspector.WindowInspectorCompat
import org.hamcrest.Matcher
import java.util.regex.Pattern

object Dialogs {

    fun countWithTag(tag: String): Int = TestEnvironment.onMainThread {
        host()?.supportFragmentManager?.fragments?.count { it.tag == tag } ?: 0
    }

    fun isShowing(tag: String): Boolean = TestEnvironment.onMainThread {
        (host()?.supportFragmentManager?.findFragmentByTag(tag) as? DialogFragment)?.dialog?.isShowing == true
    }

    fun textOf(tag: String, viewId: Int): String? = TestEnvironment.onMainThread {
        val dialog = (host()?.supportFragmentManager?.findFragmentByTag(tag) as? DialogFragment)?.dialog
        dialog?.findViewById<TextView>(viewId)?.text?.toString()
    }

    fun awaitShown(tag: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the dialog tagged $tag is showing", timeoutMs) { isShowing(tag) }
    }

    fun awaitGone(tag: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("no dialog tagged $tag remains", timeoutMs) { countWithTag(tag) == 0 }
    }

    fun tapPositive() {
        var handled: Boolean? = null
        Await.until("the positive dialog button is shown, enabled, and clickable") {
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(object : ViewAction {
                override fun getConstraints(): Matcher<View> = isDisplayed()

                override fun getDescription(): String = "activate an interactable positive dialog button without injecting a touch"

                override fun perform(uiController: UiController, view: View) {
                    if (view.isShown && view.isEnabled && view.isClickable) {
                        handled = view.performClick()
                    }
                }
            })
            handled != null
        }
        if (handled != true) {
            throw ConditionTimeoutException("The positive dialog button did not handle performClick()")
        }
        TestEnvironment.waitForIdleSync()
    }

    fun tapNegative() {
        var handled: Boolean? = null
        Await.until("the negative dialog button is shown, enabled, and clickable") {
            onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(object : ViewAction {
                override fun getConstraints(): Matcher<View> = isDisplayed()

                override fun getDescription(): String = "activate an interactable negative dialog button without injecting a touch"

                override fun perform(uiController: UiController, view: View) {
                    if (view.isShown && view.isEnabled && view.isClickable) {
                        handled = view.performClick()
                    }
                }
            })
            handled != null
        }
        if (handled != true) {
            throw ConditionTimeoutException("The negative dialog button did not handle performClick()")
        }
        TestEnvironment.waitForIdleSync()
    }

    fun dialogRoot(): View {
        var captured: View? = null
        onView(isRoot()).inRoot(isDialog()).check { view, error ->
            if (error != null) throw error
            captured = view
        }
        return captured ?: throw ConditionTimeoutException("The open dialog window has no root view")
    }

    fun <T : View> firstDescendant(root: View, type: Class<T>, predicate: (T) -> Boolean = { true }): T? {
        if (type.isInstance(root)) {
            val candidate = type.cast(root)!!
            if (predicate(candidate)) return candidate
        }
        if (root !is ViewGroup) return null
        for (index in 0 until root.childCount) {
            firstDescendant(root.getChildAt(index), type, predicate)?.let { return it }
        }
        return null
    }

    fun isAnyShowing(): Boolean =
        SuiteTiming.measure("dialog.buttonsQuery") {
            val local = fromAppWindows { roots ->
                FRAMEWORK_BUTTON_IDS.any { visibleView(roots, it) != null }
            }
            local?.value ?: FRAMEWORK_BUTTON_NAMES.any {
                TestEnvironment.uiDevice.hasObject(By.res(ANDROID_PACKAGE, it))
            }
        }

    fun awaitAnyShowing(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("a dialog carrying its framework buttons is on screen", timeoutMs) { isAnyShowing() }
    }

    fun hasViewOnScreen(@IdRes viewId: Int): Boolean =
        SuiteTiming.measure("dialog.viewQuery") {
            val local = fromAppWindows { roots -> visibleView(roots, viewId) != null }
            local?.value ?: TestEnvironment.uiDevice.hasObject(By.res(packageNameOf(viewId), entryNameOf(viewId)))
        }

    fun awaitViewOnScreen(@IdRes viewId: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the view ${entryNameOf(viewId)} is on screen", timeoutMs) { hasViewOnScreen(viewId) }
    }
    fun tapViewOnScreen(@IdRes viewId: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        val entryName = entryNameOf(viewId)
        val selector = By.res(packageNameOf(viewId), entryName)
        val view = Await.untilNotNull("the view $entryName can be tapped", timeoutMs) {
            TestEnvironment.uiDevice.findObject(selector)
        }
        view.click()
        TestEnvironment.uiDevice.waitForIdle()
        TestEnvironment.waitForIdleSync()
    }


    fun awaitViewGone(@IdRes viewId: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the view ${entryNameOf(viewId)} leaves the screen", timeoutMs) { !hasViewOnScreen(viewId) }
    }

    fun currentDialogTitle(): String? =
        SuiteTiming.measure("dialog.titleQuery") {
            val local = fromAppWindows { roots ->
                (visibleView(roots, androidx.appcompat.R.id.alertTitle) as? TextView)?.text?.toString()
            }
            if (local != null) local.value else
                TestEnvironment.uiDevice.findObject(By.res(TestEnvironment.targetPackage, ALERT_TITLE))?.text
        }

    private data class WindowQuery<T>(val value: T)

    private fun <T> fromAppWindows(read: (List<View>) -> T): WindowQuery<T>? = TestEnvironment.onMainThread {
        val roots = WindowInspectorCompat.getGlobalWindowViews().asReversed().filter {
            it.context.packageName == TestEnvironment.targetPackage && it.isAttachedToWindow &&
                it.windowVisibility == View.VISIBLE && it.isShown
        }
        if (roots.none { it.hasWindowFocus() }) return@onMainThread null
        WindowQuery(read(roots))
    }

    private fun visibleView(roots: List<View>, @IdRes viewId: Int): View? =
        roots.firstNotNullOfOrNull { root ->
            root.findViewById<View>(viewId)?.takeIf {
                it.isShown && it.getGlobalVisibleRect(Rect())
            }
        }

    fun hasDialogTitled(@StringRes titleRes: Int): Boolean = currentDialogTitle() == titleOf(titleRes)

    fun awaitDialogTitled(@StringRes titleRes: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        val expected = titleOf(titleRes)
        Await.untilValue(
            "the dialog on screen is the one titled \"$expected\", and the observed value below is the " +
                "title actually found, so a different title means the wrong dialog is up rather than none",
            timeoutMs,
            read = { currentDialogTitle() },
        ) { it == expected }
    }

    fun awaitDialogTitleGone(@StringRes titleRes: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        val expected = titleOf(titleRes)
        Await.untilValue(
            "the dialog titled \"$expected\" leaves the screen, the observed value below is the title still found",
            timeoutMs,
            read = { currentDialogTitle() },
        ) { it != expected }
    }

    private fun titleOf(@StringRes titleRes: Int): String = TestEnvironment.onMainThread {
        host()?.getString(titleRes) ?: TestEnvironment.targetContext.getString(titleRes)
    }

    fun hasDialogWithView(@IdRes viewId: Int): Boolean = isAnyShowing() && hasViewOnScreen(viewId)

    fun awaitDialogWithView(@IdRes viewId: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("a dialog carrying ${entryNameOf(viewId)} is on screen", timeoutMs) {
            hasDialogWithView(viewId)
        }
    }

    fun hasEditFieldOnScreen(): Boolean =
        isAnyShowing() && TestEnvironment.uiDevice.hasObject(By.clazz(EDIT_TEXT_CLASS))

    fun awaitEditFieldOnScreen(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("a dialog carrying a text field is on screen", timeoutMs) { hasEditFieldOnScreen() }
    }

    fun hasText(text: String): Boolean = TestEnvironment.uiDevice.hasObject(By.text(text))

    fun awaitText(text: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("a window showing \"$text\" is on screen", timeoutMs) { hasText(text) }
    }

    fun awaitTextGone(text: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the text \"$text\" leaves the screen", timeoutMs) { !hasText(text) }
    }

    fun actionTileSelector(label: String): BySelector = By.desc(label).clazz(ACTION_TILE_CLASS)

    fun hasActionTile(label: String): Boolean = hasRenderedActionTile(label)

    fun hasAnyActionTile(): Boolean = hasRenderedActionTile(null)

    private fun hasRenderedActionTile(label: String?): Boolean = SuiteTiming.measure("dialog.actionQuery") {
        val local = fromAppWindows { roots -> roots.any { containsVisibleActionTile(it, label) } }
        local?.value ?: TestEnvironment.uiDevice.hasObject(
            if (label != null) actionTileSelector(label) else
                By.clazz(ACTION_TILE_CLASS).desc(ACTION_TILE_DESCRIPTION).pkg(TestEnvironment.targetPackage)
        )
    }

    private fun containsVisibleActionTile(view: View, label: String?): Boolean {
        if (!view.isShown) return false
        val description = view.contentDescription?.toString()
        if (view.accessibilityClassName.toString() == ACTION_TILE_CLASS && description != null &&
            (if (label == null) ACTION_TILE_DESCRIPTION.matcher(description).matches() else description == label) &&
            view.getGlobalVisibleRect(Rect())
        ) return true
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                if (containsVisibleActionTile(view.getChildAt(index), label)) return true
            }
        }
        return false
    }

    fun awaitActionsDialog(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until(
            "the reader actions dialog is on screen, probed by any rendered tile rather than a named one " +
                "because the dialog scrolls and a named tile below the fold is absent from the node tree",
            timeoutMs,
        ) { hasAnyActionTile() }
    }

    private fun entryNameOf(@IdRes viewId: Int): String =
        TestEnvironment.targetContext.resources.getResourceEntryName(viewId)

    private fun packageNameOf(@IdRes viewId: Int): String =
        TestEnvironment.targetContext.resources.getResourcePackageName(viewId)

    private fun host(): AppCompatActivity? = TestEnvironment.resumedActivity() as? AppCompatActivity

    private const val ANDROID_PACKAGE = "android"
    private const val EDIT_TEXT_CLASS = "android.widget.EditText"
    private const val ACTION_TILE_CLASS = "android.widget.LinearLayout"
    private val ACTION_TILE_DESCRIPTION = Pattern.compile(".+")
    private const val ALERT_TITLE = "alertTitle"
    private val FRAMEWORK_BUTTON_NAMES = listOf("button1", "button2", "button3")
    private val FRAMEWORK_BUTTON_IDS = listOf(android.R.id.button1, android.R.id.button2, android.R.id.button3)
}
