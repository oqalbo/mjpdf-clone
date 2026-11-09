// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.artifacts

import android.content.Intent
import android.net.Uri
import android.view.View
import androidx.test.core.app.ActivityScenario
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.pdf.PDF
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.SafPickerRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.LocaleResetRule
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Rule
import org.junit.rules.RuleChain
import java.io.File

abstract class ArtifactTestCase {

    protected val suite = SuiteRules.standard()
    protected val locale = LocaleResetRule()
    protected val cleanup = ArtifactCleanupRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(locale).around(suite).around(cleanup)

    protected val reader = ArtifactReader()
    protected val picker = SafPickerRobot()

    protected fun fixture(name: String): File = ArtifactFixtures.require(name)

    protected fun downloads(): File = AppDirectories.publicDownloads

    protected fun openReader(file: File, incognito: Boolean = false): ActivityScenario<MainActivity> {
        val intent = ReaderRobot.viewIntent(file)
        if (incognito) intent.putExtra(PDF.incognitoKey, true)
        val scenario = ActivityScenario.launch<MainActivity>(intent)
        reader.awaitReady()
        return scenario
    }

    protected fun openReader(uri: Uri): ActivityScenario<MainActivity> {
        val scenario = ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(uri))
        reader.awaitReady()
        return scenario
    }

    protected fun openProtectedReader(file: File, password: String): ActivityScenario<MainActivity> {
        val scenario = ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(file))
        PasswordPrompt.enter(password)
        reader.awaitReady()
        return scenario
    }

    protected fun openThroughPicker(fileName: String): ActivityScenario<MainActivity> {
        val scenario = ActivityScenario.launch<MainActivity>(
            Intent(TestEnvironment.targetContext, MainActivity::class.java)
        )
        val pickerOpenedItself = runCatching { picker.awaitShown(Await.SHORT_TIMEOUT_MS) }.isSuccess
        if (!pickerOpenedItself) {
            TestEnvironment.onMainThread {
                val activity = TestEnvironment.resumedActivityOfType(MainActivity::class.java)
                    ?: throw ConditionTimeoutException("The empty reader did not come up to pick a file from")
                val button = activity.findViewById<View>(R.id.pickFileButton)
                    ?: throw ConditionTimeoutException("The empty reader carries no pick file button")
                button.performClick()
            }
        }
        picker.awaitShown()
        picker.navigateToDownloads()
        picker.chooseFileNamed(fileName)
        reader.awaitReady()
        return scenario
    }

    protected fun saveCopyAs(name: String): File {
        val target = File(downloads(), name)
        cleanup.own(target)
        reader.chooseSaveAsCopy()
        picker.awaitShown()
        picker.navigateToDownloads()
        picker.typeFileNameAndSave(name)
        reader.awaitSaveSettled()
        Await.until("the produced copy ${target.absolutePath} appears", Await.VERY_LONG_TIMEOUT_MS) {
            target.isFile && target.length() > 0L
        }
        return target
    }

    protected fun saveInPlace() {
        reader.chooseUpdateSameFile()
        reader.awaitSaveSettled()
    }

    protected fun copyFixtureToDownloads(fixtureName: String, targetName: String): File {
        Fixtures.ensureAllFilesAccess()
        val target = File(downloads(), targetName)
        cleanup.own(target)
        Fixtures.pushBytes(ArtifactFixtures.require(fixtureName).readBytes(), target)
        Fixtures.scanAndWait(target)
        return target
    }

    protected fun leaveReader() {
        val activity = reader.activityOrNull() ?: return
        TestEnvironment.onMainThread { activity.finish() }
        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
    }

    protected companion object {
        const val SMALL_GROWTH_BUDGET = 131_072L
        const val GROWTH_BUDGET = 262_144L
        const val NO_CHANGE_BUDGET = 65_536L
    }
}
