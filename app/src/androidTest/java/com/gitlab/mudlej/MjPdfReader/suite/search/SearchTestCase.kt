// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.search

import androidx.test.core.app.ActivityScenario
import com.gitlab.mudlej.MjPdfReader.core.debug.SearchTestHooks
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Rotation
import com.gitlab.mudlej.MjPdfReader.suite.core.SearchIdle
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.suite.state.ReaderState
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.After
import org.junit.Before
import org.junit.Rule
import java.io.File

abstract class SearchTestCase {

    @get:Rule
    val rules = SuiteRules.standard()

    protected val reader = ReaderRobot()
    protected val robot = SearchRobot(reader)
    protected val results = SearchResultsScreen()

    private val recorders = mutableListOf<SearchOracle.Recorder>()

    @Before
    fun seedSearchBaseline() {
        SettingsSeeder.suppressOnboarding()
    }

    @After
    fun releaseSearchState() {
        recorders.forEach { runCatching { it.release() } }
        recorders.clear()
        runCatching { SearchIdle.clear() }
        runCatching { SearchTestHooks.reset() }
        Rotation.reset()
    }

    protected fun fixture(name: String): File = SearchFixtures.place(name, rules.testDirectory.directory())

    protected fun openReader(name: String = SearchFixtures.TEXT, block: (File) -> Unit) {
        val file = fixture(name)
        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(file)).use {
            robot.awaitReaderReady()
            block(file)
        }
    }

    protected fun seedIgnoreAccents(value: Boolean) {
        SettingsSeeder.seed(Preferences.searchIgnoreAccentsKey, value)
    }

    protected fun seedZoomToResult(value: Boolean) {
        SettingsSeeder.seed(Preferences.searchZoomToResultKey, value)
    }

    protected fun state(): ReaderState = reader.readState()

    protected fun pageIndex(): Int = state().page
        ?: throw AssertionError("The reader state hook reports no current page")

    protected fun fileHash(): String = robot.fileHash()

    protected fun record(fileHash: String, query: String, ignoreAccents: Boolean = false): SearchOracle.Recorder {
        val recorder = SearchOracle.awaitSubscribed(fileHash, query, ignoreAccents)
        recorders.add(recorder)
        return recorder
    }

    protected fun awaitSettledOnPage(pageIndex: Int, timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        reader.awaitPageIndex(pageIndex, timeoutMs)
        reader.awaitMotionSettled()
        reader.awaitRenderIdle()
    }

    protected fun awaitReaderInteractive() {
        val before = pageIndex()
        reader.swipeForward()
        reader.awaitState("a swipe moves the reader off page $before", INTERACTIVE_TIMEOUT_MS) {
            it.page != null && it.page != before
        }
    }

    protected fun preferenceBoolean(key: String): Any? = TestEnvironment.defaultPreferences.all[key]

    companion object {
        const val INTERACTIVE_TIMEOUT_MS = 1_000L
    }
}
