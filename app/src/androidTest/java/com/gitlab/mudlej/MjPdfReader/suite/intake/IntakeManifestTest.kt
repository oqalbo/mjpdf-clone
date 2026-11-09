// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.intake

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.pdf.PDF
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class IntakeManifestTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val packageManager: PackageManager get() = TestEnvironment.targetContext.packageManager

    private val appPackage: String get() = TestEnvironment.targetPackage

    @Test
    @TestId("INTAKE-04")
    fun onlyTheLauncherAliasAndTheReaderAreExported() {
        val activities = declaredActivities()

        val exported = activities.values.filter { it.exported }.map { it.name }.sorted()
        assertEquals(
            "exactly the launcher alias and the reader may be exported",
            listOf("$appPackage.Launcher", "$appPackage.ui.reader.MainActivity").sorted(),
            exported,
        )

        for (className in NOT_EXPORTED_CLASSES) {
            val info = activities[className]
                ?: throw AssertionError(
                    "$className is not declared by the installed package, only ${activities.keys.sorted()} are"
                )
            assertFalse("$className must not be exported", info.exported)
        }
    }

    @Test
    @TestId("INTAKE-05")
    fun theShareFilterAcceptsTextLinksAndNotPdfStreams() {
        val textHandlers = componentNamesOf(query(sendIntent("text/plain")))
        assertTrue(
            "the reader must answer a shared text link, resolved set was $textHandlers",
            "$appPackage.ui.reader.MainActivity" in textHandlers,
        )

        val pdfHandlers = componentNamesOf(query(sendIntent(PDF.FILE_TYPE)))
        assertEquals(
            "the app must not advertise a PDF receiving capability it does not implement",
            emptyList<String>(),
            pdfHandlers,
        )
    }

    private fun sendIntent(mimeType: String): Intent =
        Intent(Intent.ACTION_SEND).setType(mimeType)

    private fun declaredActivities(): Map<String, ActivityInfo> {
        val flags = PackageManager.GET_ACTIVITIES or PackageManager.MATCH_DISABLED_COMPONENTS
        val info = packageManager.getPackageInfo(appPackage, flags)
        return (info.activities ?: emptyArray()).associateBy { it.name }
    }

    private fun query(intent: Intent): List<ResolveInfo> =
        packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)

    private fun componentNamesOf(resolved: List<ResolveInfo>): List<String> =
        resolved.filter { it.activityInfo.packageName == appPackage }.map { it.activityInfo.name }

    private companion object {

        const val PACKAGE = "com.gitlab.mudlej.MjPdfReader"

        val NOT_EXPORTED_CLASSES = listOf(
            "$PACKAGE.ui.text_mode.TextModeActivity",
            "$PACKAGE.ui.gotopage.GoToPageActivity",
            "$PACKAGE.ui.search.SearchActivity",
            "$PACKAGE.ui.tableofcontents.TableOfContentsActivity",
            "$PACKAGE.ui.home.HomeActivity",
        )
    }
}
