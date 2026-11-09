// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.actions

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.actions.ConfigurableAction
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class ReloadAndIncognitoTest {

    private val suite = SuiteRules.standard()
    private val cleanup = ActionsCleanupRule.forFixtures(ActionsFixtures.LARGE, ActionsFixtures.NORMAL)

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(suite).around(cleanup)

    private val act = ActionsRobot()
    private val reader = act.reader()

    @Test
    @TestId("ACT-46")
    @TestDuration(DurationCategory.SHORT)
    fun reloadReReadsTheFileWithoutLosingTheReadersPlace() {
        ActionsPreferences.seedPrimaryAction(ConfigurableAction.RELOAD)

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(ActionsFixtures.uri(ActionsFixtures.LARGE))).use {
            reader.awaitShown()
            reader.awaitLoaded(Await.LONG_TIMEOUT_MS)
            reader.awaitRenderIdle()
            val fileHash = reader.awaitFileHashResolved()

            act.actionsDialog.open()
            act.actionsDialog.tap(ActionLabels.of(ConfigurableAction.GO_TO_PAGE))
            GoToPageEntry.awaitShown()
            GoToPageEntry.goTo(TARGET_ONE_BASED_PAGE)

            reader.awaitPageIndex(TARGET_PAGE_INDEX, Await.LONG_TIMEOUT_MS)
            reader.awaitRenderIdle()

            ToolbarItems.awaitLabel(ToolbarItems.primaryId, ActionLabels.of(ConfigurableAction.RELOAD))
            val previous = TestEnvironment.resumedActivityOfType(MainActivity::class.java) as MainActivity
            ToolbarItems.tapPrimary()

            Screens.awaitReplacement(previous, MainActivity::class.java)
            reader.awaitLoaded(Await.LONG_TIMEOUT_MS)
            val state = reader.awaitPageIndex(TARGET_PAGE_INDEX, Await.LONG_TIMEOUT_MS)
            reader.awaitRenderIdle()

            assertEquals("an unchanged document keeps its identity", fileHash, state.fileHash)
            assertEquals(
                "reload must land back on the page the reader was showing, zero based",
                TARGET_PAGE_INDEX as Int?,
                state.page,
            )
            Records.awaitStoredPage(fileHash, TARGET_PAGE_INDEX, Await.LONG_TIMEOUT_MS)
        }
    }

    @Test
    @TestId("ACT-47")
    fun incognitoReportsItselfBackInTheSamePlaceItWasTurnedOn() {
        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(ActionsFixtures.uri(ActionsFixtures.NORMAL))).use {
            act.awaitReady()
            val fileHash = reader.awaitFileHashResolved()
            val recordBefore = Records.awaitRowExists(fileHash)
            val lastOpenedBefore = recordBefore.lastOpened

            act.actionsDialog.open()
            act.actionsDialog.tap(ActionLabels.incognito(active = false))

            act.awaitReaderResumed()
            reader.awaitLoaded(Await.LONG_TIMEOUT_MS)
            reader.awaitRenderIdle()

            act.actionsDialog.open()
            act.actionsDialog.awaitTile(ActionLabels.incognito(active = true))
            act.actionsDialog.assertNoTile(ActionLabels.incognito(active = false))
            act.actionsDialog.dismiss()

            assertEquals(
                "an incognito session must leave the reading history untouched",
                lastOpenedBefore,
                Records.byHash(fileHash)?.lastOpened,
            )
        }
    }

    private companion object {
        const val TARGET_ONE_BASED_PAGE = 50
        const val TARGET_PAGE_INDEX = 49
    }
}
