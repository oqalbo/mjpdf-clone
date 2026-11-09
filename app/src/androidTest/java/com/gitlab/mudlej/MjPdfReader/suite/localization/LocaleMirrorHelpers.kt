// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.localization

import android.app.Activity
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.debug.ReaderTestHooks
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationDialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.RawInput
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.actions.ConfigurableAction
import com.gitlab.mudlej.MjPdfReader.ui.tableofcontents.TableOfContentsActivity
import com.gitlab.mudlej.MjPdfReader.ui.userbookmarks.UserBookmarksActivity
import com.gitlab.mudlej.MjPdfReader.ui.userhighlights.UserHighlightsActivity
import com.gitlab.mudlej.MjPdfReader.ui.usernotes.UserNotesActivity

object LocaleFullscreen {

    const val TAP_HOLD_MS = 60L
    const val PANEL_TIMEOUT_MS = 6_000L

    fun enter() {
        Await.untilNoThrow("the fullscreen toolbar item accepts a tap") {
            onView(withId(R.id.toolbarPrimaryActionOption)).perform(ViewActions.click())
        }
        TestEnvironment.waitForIdleSync()
    }

    fun revealPanel() {
        repeat(REVEAL_ATTEMPTS) {
            if (panelVisibility() == View.VISIBLE) return
            tapCanvas()
            val revealed = runCatching { awaitPanelVisibility(View.VISIBLE) }.isSuccess
            if (revealed) return
        }
        throw ConditionTimeoutException(
            "Tapping the page never revealed the fullscreen panel after $REVEAL_ATTEMPTS taps"
        )
    }

    fun hidePanel() {
        tapCanvas()
    }

    fun awaitPanelGone() {
        awaitPanelVisibility(View.GONE)
    }

    fun hideTargetTranslationX(): Float = ReaderTestHooks.fullScreenPanelHideTargetTranslationX

    fun awaitHideTargetTranslationX(): Float = Await.untilValue(
        "the fullscreen panel publishes its computed hide translation",
        PANEL_TIMEOUT_MS,
        read = { hideTargetTranslationX() },
    ) { it != 0f }

    fun panelTranslationX(): Float = ReaderTestHooks.fullScreenPanelTranslationX

    fun panelVisibility(): Int = TestEnvironment.onMainThread {
        TestEnvironment.resumedActivityOfType(MainActivity::class.java)
            ?.findViewById<View>(R.id.fullScreenButtonsLayout)?.visibility ?: View.GONE
    }

    private fun awaitPanelVisibility(expected: Int) {
        Await.untilValue(
            "the fullscreen panel reaches visibility $expected",
            PANEL_TIMEOUT_MS,
            read = { panelVisibility() },
        ) { it == expected }
    }

    private fun tapCanvas() {
        val centre = TestEnvironment.onMainThread {
            val activity = TestEnvironment.resumedActivityOfType(MainActivity::class.java)
                ?: throw ConditionTimeoutException("No reader is resumed to tap")
            val view = activity.findViewById<View>(R.id.pdfView)
                ?: throw ConditionTimeoutException("The reader carries no pdfView")
            centreOf(view)
        }
        RawInput.pressAndHold(centre, TAP_HOLD_MS)
        TestEnvironment.waitForIdleSync()
    }

    private const val REVEAL_ATTEMPTS = 3
}

object LocaleRowGeometry {

    fun firstRowTitleBounds(title: String): Rect {
        val view = findTextView(title)
        return ScreenDirection.boundsOnScreen(view)
    }

    fun leadingIconBoundsFor(title: String): Rect {
        val icon = TestEnvironment.onMainThread {
            val titleView = findTextViewOnMain(title)
            val row = rowContainerOf(titleView)
                ?: throw ConditionTimeoutException("The row titled \"$title\" has no container")
            shownImageIn(row)
                ?: throw ConditionTimeoutException("The row titled \"$title\" carries no leading icon to measure")
        }
        return ScreenDirection.boundsOnScreen(icon)
    }

    private fun findTextView(text: String): View = TestEnvironment.onMainThread { findTextViewOnMain(text) }

    private fun findTextViewOnMain(text: String): TextView {
        val activity = TestEnvironment.resumedActivity()
            ?: throw ConditionTimeoutException("No activity is resumed to measure a row on")
        return Dialogs.firstDescendant(activity.window.decorView, TextView::class.java) {
            it.text?.toString() == text && it.isShown
        }
            ?: throw ConditionTimeoutException("No view on screen reads \"$text\"")
    }

    private fun shownImageIn(group: ViewGroup): ImageView? =
        Dialogs.firstDescendant(group, ImageView::class.java) { it.isShown }

    private fun rowContainerOf(view: View): ViewGroup? {
        var parent = view.parent
        var depth = 0
        while (parent is ViewGroup && depth < MAX_ROW_DEPTH) {
            if (shownImageIn(parent) != null) return parent
            parent = parent.parent
            depth++
        }
        return null
    }

    private const val MAX_ROW_DEPTH = 4
}

data class ListScreen(val action: ConfigurableAction, val type: Class<out Activity>, @StringRes val titleRes: Int)

object LocaleLists {

    fun screens(): List<ListScreen> = listOf(
        ListScreen(ConfigurableAction.USER_BOOKMARKS, UserBookmarksActivity::class.java, R.string.bookmarks),
        ListScreen(
            ConfigurableAction.USER_HIGHLIGHTS,
            UserHighlightsActivity::class.java,
            R.string.user_highlights_title,
        ),
        ListScreen(ConfigurableAction.USER_NOTES, UserNotesActivity::class.java, R.string.user_notes_title),
        ListScreen(
            ConfigurableAction.TABLE_OF_CONTENTS,
            TableOfContentsActivity::class.java,
            R.string.table_of_contents,
        ),
    )

    fun openFromActions(screen: ListScreen) {
        LocaleActions.openActionsDialog()
        LocaleActions.tapDialogEntry(AnnotationDialogs.string(screen.action.titleRes))
        Screens.awaitResumed(screen.type, Await.LONG_TIMEOUT_MS)
    }

    fun titleOf(type: Class<out Activity>): String = TestEnvironment.onMainThread {
        resumedActivityOrThrow(type).title?.toString().orEmpty()
    }

    fun returnToReader() {
        TestEnvironment.uiDevice.pressBack()
        Screens.awaitResumed(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
    }
}
