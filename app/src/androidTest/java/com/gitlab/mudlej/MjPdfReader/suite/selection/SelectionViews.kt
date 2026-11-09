// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.selection

import android.view.View
import androidx.annotation.IdRes
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.uiautomator.By
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment

object SelectionViews {

    fun <T> read(@IdRes viewId: Int, reader: (View) -> T): T {
        var captured: Any? = NOTHING
        onView(withId(viewId)).check { view, notFound ->
            if (notFound != null) throw notFound
            captured = reader(view)
        }
        if (captured === NOTHING) {
            throw AssertionError("Espresso matched ${name(viewId)} but the read never ran")
        }
        @Suppress("UNCHECKED_CAST")
        return captured as T
    }

    fun exists(@IdRes viewId: Int): Boolean =
        TestEnvironment.uiDevice.hasObject(By.res(packageOf(viewId), name(viewId)))

    fun visibility(@IdRes viewId: Int): Int = read(viewId) { it.visibility }

    fun assertNeverAppears(@IdRes viewId: Int, windowMs: Long = ABSENCE_WINDOW_MS) {
        Await.assertThroughout(
            condition = "${name(viewId)} never appears in ${WINDOW_SET}",
            windowMs = windowMs,
        ) { !exists(viewId) }
    }

    private fun packageOf(@IdRes viewId: Int): String =
        TestEnvironment.targetContext.resources.getResourcePackageName(viewId)

    fun name(@IdRes viewId: Int): String =
        runCatching { TestEnvironment.targetContext.resources.getResourceEntryName(viewId) }
            .getOrDefault(viewId.toString())

    fun string(resId: Int, vararg formatArgs: Any): String =
        if (formatArgs.isEmpty()) {
            TestEnvironment.targetContext.getString(resId)
        } else {
            TestEnvironment.targetContext.getString(resId, *formatArgs)
        }

    const val ABSENCE_WINDOW_MS = 1_500L

    const val WINDOW_SET =
        "the accessibility node tree of every window on screen, which covers the activity, its dialogs " +
            "and its bottom sheets"

    private val NOTHING = Any()
}
