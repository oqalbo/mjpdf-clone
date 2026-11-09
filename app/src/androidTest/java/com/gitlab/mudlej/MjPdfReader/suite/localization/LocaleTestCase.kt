// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.localization

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.test.core.app.ActivityScenario
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.SelectionRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.DisplaySizeRule
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.state.ReaderState
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsActivity
import org.junit.Rule
import org.junit.rules.RuleChain
import java.io.File

abstract class LocaleTestCase {

    protected val suite = SuiteRules.standard()
    protected val locale = LocaleRule()
    protected val displaySize = DisplaySizeRule()
    private val localeRecords = LocaleRecordsRule()

    @get:Rule
    val rules: RuleChain =
        RuleChain.outerRule(displaySize).around(locale).around(suite).around(localeRecords)

    protected val reader = ReaderRobot()
    protected val selection = SelectionRobot()
    protected val home = HomeRobot()

    protected fun useLocale(tag: String) {
        locale.set(tag)
    }

    protected fun fixture(name: String): File = LocaleFixtures.place(name, suite.testDirectory.directory())

    protected fun openReader(name: String = LocaleFixtures.NORMAL, body: (File) -> Unit) {
        val file = fixture(name)
        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(file)).use {
            awaitReaderReady()
            body(file)
        }
    }

    protected fun openReaderScenario(name: String = LocaleFixtures.NORMAL): Pair<ActivityScenario<MainActivity>, File> {
        val file = fixture(name)
        val scenario = ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(file))
        awaitReaderReady()
        return scenario to file
    }

    protected fun awaitReaderReady(): ReaderState =
        reader.awaitReadyAtFullQuality(Await.LONG_TIMEOUT_MS)

    protected fun launchHome(): ActivityScenario<HomeActivity> {
        val scenario = ActivityScenario.launch(HomeActivity::class.java)
        home.awaitShown(Await.LONG_TIMEOUT_MS)
        home.awaitRendered(Await.LONG_TIMEOUT_MS)
        return scenario
    }

    protected fun launchSettings(): ActivityScenario<SettingsActivity> {
        val scenario = ActivityScenario.launch(SettingsActivity::class.java)
        Screens.awaitResumed(SettingsActivity::class.java, Await.LONG_TIMEOUT_MS)
        return scenario
    }

    protected fun localized(@StringRes resId: Int): String =
        LocalizedStrings.string(requireTag(), resId)

    protected fun localized(@StringRes resId: Int, vararg formatArgs: Any): String =
        LocalizedStrings.string(requireTag(), resId, *formatArgs)

    protected fun localizedPlural(@PluralsRes resId: Int, quantity: Int, vararg formatArgs: Any): String =
        LocalizedStrings.plural(requireTag(), resId, quantity, *formatArgs)

    protected fun translated(@StringRes resId: Int): String =
        LocalizedStrings.requireTranslated(requireTag(), resId)

    protected fun english(@StringRes resId: Int): String = LocalizedStrings.english(resId)

    protected fun appLanguageInProcess(): String = locale.currentLanguageInAppProcess()

    protected fun visibleTexts(): List<String> =
        TestEnvironment.uiDevice.findObjects(androidx.test.uiautomator.By.pkg(TestEnvironment.targetPackage))
            .mapNotNull { it.text }
            .filter { it.isNotBlank() }
            .distinct()

    private fun requireTag(): String = locale.appliedTag()
        ?: throw AssertionError(
            "No app locale has been applied, so a localized lookup would silently read the English value and " +
                "the test would pass without proving anything. Call useLocale first."
        )

    protected companion object {
        const val ARABIC = "ar-EG"
        const val PERSIAN = "fa-IR"
        const val RUSSIAN = "ru-RU"
        const val UKRAINIAN = "uk-UA"
        const val GERMAN = "de-DE"
        const val TURKISH = "tr-TR"
        const val HEBREW = "he-IL"
    }
}
