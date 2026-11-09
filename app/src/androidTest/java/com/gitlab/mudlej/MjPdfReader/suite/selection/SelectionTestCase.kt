// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.selection

import androidx.test.core.app.ActivityScenario
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.SelectionRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.IntentStubs
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.suite.state.ReaderState
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.share.ShareQuoteDebug
import org.junit.After
import org.junit.Before
import org.junit.Rule
import java.io.File

abstract class SelectionTestCase {

    protected val suite = SuiteRules.standard()

    @get:Rule
    val rules: SuiteRules = suite

    protected val reader = ReaderRobot()
    protected val selectionRobot = SelectionRobot()
    protected val driver = SelectionDriver(reader, selectionRobot)

    @Before
    fun clearSharedSelectionState() {
        IntentStubs.clearClipboard()
        SelectionShare.clearCache()
        SelectionDictionary.remove()
    }

    @After
    fun releaseSelectionState() {
        runCatching { ShareQuoteDebug.clear() }
        runCatching { SelectionShare.clearCache() }
        runCatching { SelectionDictionary.remove() }
    }

    protected fun fixture(name: String): File = SelectionFixtures.place(name, suite.testDirectory.directory())

    protected fun pageTextOf(file: File, pageIndex: Int = 0): SelectionPageText =
        SelectionPageText.of(file, pageIndex)

    protected fun openReader(
        name: String = SelectionFixtures.LATIN,
        body: (ActivityScenario<MainActivity>, File) -> Unit,
    ) {
        val file = fixture(name)
        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(file)).use { scenario ->
            awaitReaderReady()
            body(scenario, file)
        }
    }

    protected fun openLatin(body: (ActivityScenario<MainActivity>, SelectionPageText) -> Unit) {
        openReader(SelectionFixtures.LATIN) { scenario, file ->
            body(scenario, pageTextOf(file))
        }
    }

    protected fun openArabic(body: (ActivityScenario<MainActivity>, SelectionPageText) -> Unit) {
        openReader(SelectionFixtures.ARABIC) { scenario, file ->
            body(scenario, pageTextOf(file))
        }
    }

    protected fun awaitReaderReady(): ReaderState =
        reader.awaitReadyAtFullQuality(Await.LONG_TIMEOUT_MS)

    protected fun state(): ReaderState = reader.readState()

    protected fun seedWebTranslation(engine: String = "google", targetLanguage: String = "") {
        SettingsSeeder.seedAll(
            mapOf(
                Preferences.translationModeKey to Preferences.translationModeWeb,
                Preferences.translationEngineKey to engine,
                Preferences.translationTargetLanguageKey to targetLanguage,
            )
        )
    }

    protected fun clipboardText(): String? = IntentStubs.clipboardText()

    protected fun clipboardLabel(): String? = IntentStubs.clipboardLabel()

    protected fun copyAndReadClipboard(): String? {
        SelectionCard.tap(com.gitlab.mudlej.MjPdfReader.R.id.textSelectionCopyButton)
        return Await.untilValue(
            condition = "the clipboard carries the copied selection",
            timeoutMs = Await.DEFAULT_TIMEOUT_MS,
            read = { clipboardText() },
        ) { !it.isNullOrEmpty() }
    }
}
