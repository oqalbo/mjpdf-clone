// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.history

import android.view.View
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.ui.tableofcontents.TableOfContentsActivity
import org.hamcrest.CoreMatchers.allOf

object TableOfContentsRoute {

    fun openFromReader(hist: HistoryReader) {
        hist.openActionsDialog()
        hist.tapTile(TestEnvironment.targetContext.getString(R.string.table_of_contents))
        Screens.awaitResumed(TableOfContentsActivity::class.java, Await.LONG_TIMEOUT_MS)
        awaitEntriesLoaded()
    }

    fun awaitEntriesLoaded(timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        Await.until("the table of contents finishes reading its entries", timeoutMs) {
            progressVisibility() == View.GONE
        }
    }

    fun tapEntry(title: String) {
        onView(allOf(withText(title), isDisplayed())).perform(click())
        TestEnvironment.waitForIdleSync()
    }

    fun awaitResumed(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Screens.awaitResumed(TableOfContentsActivity::class.java, timeoutMs)
    }

    private fun progressVisibility(): Int? = TestEnvironment.onMainThread {
        TestEnvironment.resumedActivityOfType(TableOfContentsActivity::class.java)
            ?.findViewById<View>(R.id.progressBar)?.visibility
    }
}
