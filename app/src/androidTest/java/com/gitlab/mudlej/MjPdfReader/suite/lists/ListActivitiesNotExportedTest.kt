// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.lists

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.userhighlights.UserHighlightsActivity
import com.gitlab.mudlej.MjPdfReader.ui.usernotes.UserNotesActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class ListActivitiesNotExportedTest {

    @get:Rule
    val rules: SuiteRules = SuiteRules.standard()

    @Test
    @TestId("LIST-57")
    fun neitherListActivityCanBeStartedByAnotherApp() {
        val packageManager = TestEnvironment.targetContext.packageManager
        listOf(UserHighlightsActivity::class.java, UserNotesActivity::class.java).forEach { screen ->
            val info = activityInfo(
                packageManager,
                ComponentName(TestEnvironment.targetPackage, screen.name),
            )
            assertFalse(
                "The installed package declares ${screen.simpleName} as exported. That screen lists the contents " +
                    "of the document the user is reading and must stay closed to outside callers. This is read " +
                    "from the installed ActivityInfo rather than by trying to start it and expecting a " +
                    "SecurityException, because instrumentation runs under the app's own UID, where a start of a " +
                    "non exported activity of the same package is legal and always succeeds",
                info.exported,
            )
            assertTrue(
                "${screen.simpleName} is not declared in the installed package at all, so the export flag above " +
                    "was read from nothing",
                info.name == screen.name,
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun activityInfo(packageManager: PackageManager, component: ComponentName): ActivityInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getActivityInfo(component, PackageManager.ComponentInfoFlags.of(0L))
        } else {
            packageManager.getActivityInfo(component, 0)
        }
}
