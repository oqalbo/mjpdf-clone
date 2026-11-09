// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.actions

import org.junit.rules.TestWatcher
import org.junit.runner.Description

class ActionsCleanupRule(
    private val screenshotPrefixes: List<String> = DEFAULT_SCREENSHOT_PREFIXES,
) : TestWatcher() {

    override fun starting(description: Description) {
        screenshotPrefixes.forEach { prefix -> runCatching { ScreenshotOracle.clear(prefix) } }
        runCatching { PrintStaging.clear() }
    }

    override fun finished(description: Description) {
        screenshotPrefixes.forEach { prefix -> runCatching { ScreenshotOracle.clear(prefix) } }
        runCatching { PrintStaging.clear() }
        runCatching { ActionsSaveGate.reset() }
    }

    companion object {

        val DEFAULT_SCREENSHOT_PREFIXES: List<String> = listOf(
            ActionsFixtures.NORMAL,
            ActionsFixtures.KNOWN_TEXT,
            ActionsFixtures.EIGHT_PAGE,
            ActionsFixtures.NO_LINKS,
            ActionsFixtures.SCANNED,
            ActionsFixtures.PROTECTED,
            ActionsFixtures.TOC,
            ActionsFixtures.LARGE,
            ActionsFixtures.HIGHLIGHTS,
        ).map { ActionsFixtures.screenshotPrefix(it) }

        fun forFixtures(vararg fixtureNames: String) =
            ActionsCleanupRule(fixtureNames.map { ActionsFixtures.screenshotPrefix(it) })
    }
}
