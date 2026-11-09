// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.actions

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.pdf.PDF
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.bookmarks.TableOfContentsRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.links.LinksActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.actions.ConfigurableAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class LaunchActionsTest {

    private val suite = SuiteRules.standard()
    private val cleanup = ActionsCleanupRule.forFixtures(ActionsFixtures.TOC, ActionsFixtures.SCANNED, ActionsFixtures.NO_LINKS)

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(suite).around(cleanup)

    private val act = ActionsRobot()

    @Test
    @TestId("ACT-41")
    fun theTableOfContentsActionIsOfferedForEveryDocumentAndAlwaysHandsOverTheOpenOne() {
        val withOutline = openAndLaunchTableOfContents(ActionsFixtures.TOC)
        assertTrue(
            "the outline must contain the fixture's Introduction entry, it lists ${withOutline.entries}",
            withOutline.entries.contains(ActionsFixtures.TOC_FIRST_ENTRY),
        )
        assertEquals(
            "the contents screen names itself with the action's own string",
            ActionLabels.of(ConfigurableAction.TABLE_OF_CONTENTS),
            withOutline.title,
        )

        assertTrue("a document without an outline has no entries", openAndLaunchTableOfContents(ActionsFixtures.SCANNED).entries.isEmpty())
    }

    @Test
    @TestId("ACT-42")
    fun theLinksActionLaunchesTheLinksScreenWithTheOpenDocument() {
        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(ActionsFixtures.uri(ActionsFixtures.NO_LINKS))).use {
            act.awaitReady()
            val documentUri = act.reader().readState().documentUri

            act.runFromActionsDialog(ActionLabels.of(ConfigurableAction.LINKS_IN_FILE))

            val links = ActionsDestinations.awaitResumed(LinksActivity::class.java)
            act.actionsDialog.assertNoTile(ActionLabels.of(ConfigurableAction.LINKS_IN_FILE))

            assertEquals(
                "the links screen is handed the document the reader had open",
                documentUri,
                ActionsDestinations.stringExtra(links, PDF.filePathKey),
            )
        }
    }

    private fun openAndLaunchTableOfContents(fixtureName: String): TableOfContentsLaunch {
        var launch: TableOfContentsLaunch? = null
        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(ActionsFixtures.uri(fixtureName))).use {
            act.awaitReady()
            val documentUri = act.reader().readState().documentUri
            val pageIndex = act.reader().readState().page

            act.actionsDialog.open()
            val tileLabel = ActionLabels.of(ConfigurableAction.TABLE_OF_CONTENTS)
            act.actionsDialog.awaitTile(tileLabel)
            assertTrue(
                "the contents tile must stay clickable on $fixtureName, whether or not it has an outline",
                act.actionsDialog.tileIsClickable(tileLabel),
            )
            act.actionsDialog.tap(tileLabel)

            val contents = ActionsTableOfContents.awaitShown()
            val contentsRobot = TableOfContentsRobot()
            contentsRobot.awaitLoaded()
            contentsRobot.awaitRowCount(if (fixtureName == ActionsFixtures.TOC) ActionsFixtures.TOC_TOP_LEVEL_ENTRIES else 0)

            assertEquals(
                "the contents screen is handed the document the reader had open",
                documentUri,
                ActionsDestinations.stringExtra(contents, PDF.filePathKey),
            )
            assertEquals(
                "the contents screen is handed the page the reader was on",
                pageIndex,
                ActionsDestinations.intExtra(contents, PDF.pageNumberKey),
            )

            launch = TableOfContentsLaunch(
                title = ActionsDestinations.titleOf(contents),
                entries = contentsRobot.adapterTitles(),
            )
        }
        return launch ?: throw AssertionError("The table of contents screen never opened for $fixtureName")
    }

    private data class TableOfContentsLaunch(val title: String?, val entries: List<String>)
}
