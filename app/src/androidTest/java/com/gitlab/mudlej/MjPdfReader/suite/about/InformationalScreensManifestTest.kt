// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.about

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class InformationalScreensManifestTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val packageManager: PackageManager get() = TestEnvironment.targetContext.packageManager

    private val appPackage: String get() = TestEnvironment.targetPackage

    @Test
    @TestId("ABOUT-51")
    fun theSixLockedDownScreensAreNotExported() {
        val activities = installedActivities()

        for (className in LOCKED_DOWN_CLASSES) {
            val info = activities[className]
                ?: throw AssertionError(
                    "$className is not declared by the installed package, only ${activities.keys.sorted()} are"
                )
            assertFalse(
                "$className must not be exported",
                info.exported,
            )
            assertNull(
                "$className declares the permission ${info.permission}, which would mask an exported component. " +
                    "exported=false must be doing the work on its own.",
                info.permission,
            )
        }
    }

    @Test
    @TestId("ABOUT-52")
    fun noInformationalActivityIsReachableByAnImplicitIntent() {
        val resolved = LinkedHashSet<String>()
        implicitProbes().forEach { probe -> resolved += componentNamesOf(query(probe)) }
        packageScopedProbes().forEach { probe -> resolved += componentNamesOf(query(probe)) }

        for (className in INFORMATIONAL_CLASSES) {
            assertFalse(
                "$className answers an intent an external app could build, resolved set was $resolved",
                className in resolved,
            )
        }

        val launcherComponents = componentNamesOf(query(launcherProbe().setPackage(appPackage)))
        assertEquals(
            "the package must publish exactly one launcher component",
            listOf("$appPackage.Launcher"),
            launcherComponents.toList(),
        )

        val pdfViewComponents = componentNamesOf(query(pdfViewProbe().setPackage(appPackage)))
        assertEquals(
            "the reader must be the only component answering ACTION_VIEW for application/pdf",
            listOf("$appPackage.ui.reader.MainActivity"),
            pdfViewComponents.toList(),
        )
    }

    @Test
    @TestId("ABOUT-53")
    fun theIntroIsNotOfferedAsALauncherEntry() {
        val entries = query(launcherProbe().setPackage(appPackage))

        assertEquals("the package must publish exactly one launcher entry, found $entries", 1, entries.size)

        val entry = entries.single()
        assertEquals(
            "the launcher entry must be the alias targeting home",
            "$appPackage/$appPackage.Launcher",
            "${entry.activityInfo.packageName}/${entry.activityInfo.name}",
        )
        assertEquals(
            "the launcher entry must carry the app name",
            TestEnvironment.targetContext.getString(R.string.mj_app_name),
            entry.loadLabel(packageManager).toString(),
        )
    }

    private fun installedActivities(): Map<String, ActivityInfo> {
        val flags = PackageManager.GET_ACTIVITIES or PackageManager.MATCH_DISABLED_COMPONENTS
        val info = packageManager.getPackageInfo(appPackage, flags)
        return (info.activities ?: emptyArray()).associateBy { it.name }
    }

    private fun query(intent: Intent): List<ResolveInfo> =
        packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)

    private fun componentNamesOf(resolved: List<ResolveInfo>): List<String> =
        resolved.filter { it.activityInfo.packageName == appPackage }.map { it.activityInfo.name }

    private fun implicitProbes(): List<Intent> = listOf(
        Intent(Intent.ACTION_VIEW),
        Intent(Intent.ACTION_VIEW, Uri.parse("https://mudlej.com")),
        pdfViewProbe(),
        Intent(Intent.ACTION_MAIN),
        launcherProbe(),
    )

    private fun packageScopedProbes(): List<Intent> = listOf(
        Intent(Intent.ACTION_MAIN).setPackage(appPackage),
        Intent(Intent.ACTION_VIEW).setPackage(appPackage),
        Intent(Intent.ACTION_SEND).setPackage(appPackage),
        Intent(Intent.ACTION_SEND).setType("text/plain").setPackage(appPackage),
        launcherProbe().setPackage(appPackage),
        pdfViewProbe().setPackage(appPackage),
    )

    private fun launcherProbe(): Intent =
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

    private fun pdfViewProbe(): Intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(Uri.parse("content://com.example.documents/paper.pdf"), "application/pdf")

    private companion object {

        const val PACKAGE = "com.gitlab.mudlej.MjPdfReader"

        val INFORMATIONAL_CLASSES = listOf(
            "$PACKAGE.ui.intro.MainIntroActivity",
            "$PACKAGE.ui.about.AboutActivity",
            "$PACKAGE.ui.about.WhatsNewActivity",
            "$PACKAGE.ui.about.AppFeaturesActivity",
            "$PACKAGE.ui.about.FeatureTopicActivity",
        )

        val LOCKED_DOWN_CLASSES = INFORMATIONAL_CLASSES + "$PACKAGE.ui.settings.SettingsActivity"
    }
}
