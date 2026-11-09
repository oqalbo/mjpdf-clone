// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.robot

import android.widget.TextView
import androidx.annotation.IdRes
import androidx.test.core.app.ActivityScenario
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.ui.about.AboutActivity

class AboutRobot : Robot() {

    fun launch(): ActivityScenario<AboutActivity> {
        val scenario = ActivityScenario.launch(AboutActivity::class.java)
        awaitShown()
        return scenario
    }

    fun openFromHome(home: HomeRobot = HomeRobot()): AboutRobot {
        home.launchFromLauncher()
        home.awaitRendered()
        home.openOverflowMenu()
        home.tapMenuRow(R.id.aboutRow)
        Screens.awaitResumed(AboutActivity::class.java)
        awaitShown()
        return this
    }

    override fun awaitShown(timeoutMs: Long) {
        awaitDisplayed(R.id.versionChip, timeoutMs)
    }

    fun assertShown(): AboutRobot {
        assertDisplayed(R.id.versionChip)
        assertDisplayed(R.id.featuredCard)
        return this
    }

    fun versionChipText(): String = readView(R.id.versionChip) { (it as TextView).text.toString() }

    fun tapVersionChip(): AboutRobot {
        click(R.id.versionChip)
        waitForIdle()
        return this
    }

    fun tapFeaturedCard(): AboutRobot {
        bringIntoView(R.id.featuredCard)
        click(R.id.featuredCard)
        waitForIdle()
        return this
    }

    fun openRow(@IdRes rowId: Int): AboutRobot {
        bringIntoView(rowId)
        click(rowId)
        waitForIdle()
        return this
    }

    fun scrollToRow(@IdRes rowId: Int): AboutRobot {
        bringIntoView(rowId)
        return this
    }

    fun rowTitle(@IdRes rowId: Int): String =
        readView(rowId) { it.findViewById<TextView>(R.id.rowTitle).text.toString() }

    fun rowSubtitle(@IdRes rowId: Int): String =
        readView(rowId) { it.findViewById<TextView>(R.id.rowSubtitle).text.toString() }

    fun rowSubtitleVisibility(@IdRes rowId: Int): Int =
        readView(rowId) { it.findViewById<TextView>(R.id.rowSubtitle).visibility }

    fun pressUp(): AboutRobot {
        AppBar.pressUp()
        waitForIdle()
        return this
    }

    companion object {

        val ROW_IDS = listOf(
            R.id.whatsNewRow,
            R.id.appFeaturesRow,
            R.id.replayIntroRow,
            R.id.privacyRow,
            R.id.licenseRow,
            R.id.sourceCodeRow,
            R.id.librariesRow,
            R.id.websiteRow,
            R.id.emailRow,
            R.id.gitlabRow,
            R.id.githubRow,
        )

        val ROW_TITLE_RES = listOf(
            R.string.whats_new_title,
            R.string.features_title,
            R.string.intro,
            R.string.privacy,
            R.string.myLicense,
            R.string.code,
            R.string.libs,
            R.string.about_website,
            R.string.about_email,
            R.string.gitlab,
            R.string.github,
        )

        val SUBTITLED_ROW_IDS = listOf(R.id.websiteRow, R.id.emailRow)

        fun expectedRowTitles(): List<String> =
            ROW_TITLE_RES.map { TestEnvironment.targetContext.getString(it) }
    }
}
