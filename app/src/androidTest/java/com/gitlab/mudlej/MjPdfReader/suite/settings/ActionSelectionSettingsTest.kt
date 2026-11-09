// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.settings

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.ui.reader.actions.ConfigurableAction
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class ActionSelectionSettingsTest : SettingsTestCase() {

    @Test
    @TestId("SET-37")
    @TestDuration(DurationCategory.SHORT)
    fun thePrimaryToolbarButtonStoresTheChosenActionIdAndResetsToFullscreen() {
        launchSettingsOn(SettingsPage.CUSTOMIZE_CONTROLS).use {
            openChoiceDialog(primaryTitle)
            SettingsDialog.pickChoice(label(ConfigurableAction.SEARCH))
            SettingsDialog.awaitDismissed()

            SettingsPrefs.awaitString(Preferences.primaryButtonActionKey, ConfigurableAction.SEARCH.id)
            SettingsScreen.awaitRowSummaryIs(primaryTitle, label(ConfigurableAction.SEARCH))

            openChoiceDialog(primaryTitle)
            assertEquals(
                "the reopened picker checks the row matching the stored id",
                label(ConfigurableAction.SEARCH),
                SettingsDialog.checkedChoiceLabelOrNull(),
            )
            SettingsDialog.reset()
            SettingsDialog.awaitDismissed()

            SettingsPrefs.awaitString(Preferences.primaryButtonActionKey, Preferences.primaryButtonActionDefault)
            SettingsScreen.awaitRowSummaryIs(primaryTitle, label(ConfigurableAction.FULLSCREEN))
        }
    }

    @Test
    @TestId("SET-38")
    @TestDuration(DurationCategory.SHORT)
    fun theSecondToolbarSlotIsWrittenIndependentlyOfTheFirst() {
        launchSettingsOn(SettingsPage.CUSTOMIZE_CONTROLS).use {
            openChoiceDialog(secondaryTitle)
            SettingsDialog.pickChoice(label(ConfigurableAction.TABLE_OF_CONTENTS))
            SettingsDialog.awaitDismissed()

            SettingsPrefs.awaitString(Preferences.secondaryButtonActionKey, ConfigurableAction.TABLE_OF_CONTENTS.id)
            SettingsPrefs.assertUnset(Preferences.primaryButtonActionKey)
        }
    }

    @Test
    @TestId("SET-39")
    fun theShortcutBarDialogStoresBothTheSelectionAndTheDisplayedOrder() {
        launchSettingsOn(SettingsPage.CUSTOMIZE_CONTROLS).use {
            openRowDialog(shortcutTitle)

            SettingsDialog.setRecyclerRowChecked(label(ConfigurableAction.SEARCH), false)
            SettingsDialog.setRecyclerRowChecked(label(ConfigurableAction.GO_TO_PAGE), false)
            SettingsDialog.setRecyclerRowChecked(label(ConfigurableAction.SHARE), true)
            SettingsDialog.apply()
            SettingsDialog.awaitDismissed()

            val expected = ConfigurableAction.defaultShortcutBarActionIds -
                ConfigurableAction.SEARCH.id -
                ConfigurableAction.GO_TO_PAGE.id +
                ConfigurableAction.SHARE.id
            SettingsPrefs.awaitStringSet(Preferences.shortcutBarActionsKey, expected)

            val order = SettingsPrefs.orderList(Preferences.shortcutBarActionOrderKey)
            assertEquals(
                "the stored order lists every shortcut bar action, unchecked ones included",
                ConfigurableAction.shortcutBarActions.size,
                order.size,
            )
            val validIds = ConfigurableAction.shortcutBarActions.map { it.id }.toSet()
            assertTrue(
                "every stored order entry is a shortcut bar action id, it reads $order",
                order.all { it in validIds },
            )
            assertTrue(
                "every stored selection entry is a shortcut bar action id",
                SettingsPrefs.stringSet(Preferences.shortcutBarActionsKey).orEmpty().all { it in validIds },
            )
        }
    }

    @Test
    @TestId("SET-40")
    @TestDuration(DurationCategory.NORMAL)
    fun theShortcutBarCanBeEmptiedAndFilledAndNeitherExtremeReordersIt() {
        val expectedOrder = ConfigurableAction.shortcutBarActions.map { it.id }.reversed()
        assertTrue(
            "the reversed shortcut bar order must be committed before Settings opens",
            SettingsPrefs.store.edit()
                .putString(Preferences.shortcutBarActionOrderKey, expectedOrder.joinToString(","))
                .commit(),
        )
        launchSettingsOn(SettingsPage.CUSTOMIZE_CONTROLS).use {
            openRowDialog(shortcutTitle)
            assertEquals(
                "the dialog lists the rows in the stored custom order",
                expectedOrder,
                SettingsDialog.allRowLabelsInOrder().map(::idOfLabel),
            )
            SettingsDialog.checkOnlyRecyclerRows(emptySet())
            SettingsDialog.apply()
            SettingsDialog.awaitDismissed()

            SettingsPrefs.awaitStringSet(Preferences.shortcutBarActionsKey, emptySet())
            val orderAfterNone = SettingsPrefs.orderList(Preferences.shortcutBarActionOrderKey)
            assertEquals("clearing selection preserves the entire custom order", expectedOrder, orderAfterNone)

            openRowDialog(shortcutTitle)
            val everyLabel = SettingsDialog.allRowLabelsInOrder().toSet()
            SettingsDialog.checkOnlyRecyclerRows(everyLabel)
            SettingsDialog.apply()
            SettingsDialog.awaitDismissed()

            SettingsPrefs.awaitStringSet(
                Preferences.shortcutBarActionsKey,
                ConfigurableAction.shortcutBarActions.map { it.id }.toSet(),
            )
            assertEquals(
                "selecting every row preserves the entire custom order",
                expectedOrder,
                SettingsPrefs.orderList(Preferences.shortcutBarActionOrderKey),
            )
        }
    }

    @Test
    @TestId("SET-41")
    fun draggingAShortcutBarRowByItsHandleStoresTheArrangedOrder() {
        launchSettingsOn(SettingsPage.CUSTOMIZE_CONTROLS).use {
            openRowDialog(shortcutTitle)

            val before = SettingsDialog.allRowLabelsInOrder()
            val moved = before[2]
            val movedId = idOfLabel(moved)

            SettingsDialog.dragRecyclerRowToTop(moved)
            assertEquals(
                "the dragged row comes to rest at the top of the list",
                moved,
                SettingsDialog.firstBoundRowLabel(),
            )

            SettingsDialog.apply()
            SettingsDialog.awaitDismissed()

            val order = SettingsPrefs.orderList(Preferences.shortcutBarActionOrderKey)
            assertEquals("the row dragged to the top leads the stored order", movedId, order.first())
            assertEquals(
                "reordering moves ids around without adding or dropping any",
                before.map(::idOfLabel).toSet(),
                order.toSet(),
            )
            SettingsPrefs.assertStringSet(
                Preferences.shortcutBarActionsKey,
                ConfigurableAction.defaultShortcutBarActionIds,
            )
        }
    }

    @Test
    @TestId("SET-42")
    fun resetInsideTheShortcutBarDialogIsAProposalUntilApplyConfirmsIt() {
        launchSettingsOn(SettingsPage.CUSTOMIZE_CONTROLS).use {
            openRowDialog(shortcutTitle)
            SettingsDialog.setRecyclerRowChecked(label(ConfigurableAction.SEARCH), false)
            SettingsDialog.apply()
            SettingsDialog.awaitDismissed()
            SettingsPrefs.awaitStringSet(
                Preferences.shortcutBarActionsKey,
                ConfigurableAction.defaultShortcutBarActionIds - ConfigurableAction.SEARCH.id,
            )

            openRowDialog(shortcutTitle)
            SettingsDialog.reset()
            SettingsDialog.awaitRecyclerRowChecked(label(ConfigurableAction.SEARCH), true)
            SettingsDialog.cancel()
            SettingsDialog.awaitDismissed()
            assertFalse(
                "a cancelled reset leaves the stored selection alone",
                ConfigurableAction.SEARCH.id in SettingsPrefs.stringSet(Preferences.shortcutBarActionsKey).orEmpty(),
            )

            openRowDialog(shortcutTitle)
            SettingsDialog.reset()
            SettingsDialog.apply()
            SettingsDialog.awaitDismissed()

            SettingsPrefs.awaitStringSet(
                Preferences.shortcutBarActionsKey,
                ConfigurableAction.defaultShortcutBarActionIds,
            )
            Await.untilValue(
                "the stored order comes back as the shipped order",
                Await.DEFAULT_TIMEOUT_MS,
                read = { SettingsPrefs.orderList(Preferences.shortcutBarActionOrderKey) },
            ) { it == ConfigurableAction.defaultShortcutBarOrder.map { action -> action.id } }
        }
    }

    @Test
    @TestId("SET-43")
    fun theExitFullscreenRowCannotBeUncheckedOrDraggedOutOfFirstPlace() {
        launchSettingsOn(SettingsPage.CUSTOMIZE_CONTROLS).use {
            openRowDialog(fullScreenTitle)

            val exit = label(ConfigurableAction.EXIT_FULLSCREEN)
            assertTrue("the Exit row is checked", SettingsDialog.isRecyclerRowChecked(exit))
            assertFalse("the Exit row checkbox is locked", SettingsDialog.isRecyclerRowCheckboxEnabled(exit))
            assertFalse("the Exit row hides its drag handle", SettingsDialog.isRecyclerRowDragHandleVisible(exit))
            assertFalse("the Exit row takes no clicks", SettingsDialog.isRecyclerRowClickable(exit))

            SettingsDialog.tapRecyclerRow(exit)
            assertTrue("tapping the locked row changes nothing", SettingsDialog.isRecyclerRowChecked(exit))

            val secondRow = SettingsDialog.allRowLabelsInOrder()[1]
            SettingsDialog.dragRecyclerRowToTop(secondRow)
            assertEquals(
                "a locked row refuses to move, so Exit keeps the first position",
                exit,
                SettingsDialog.allRowLabelsInOrder().first(),
            )

            SettingsDialog.apply()
            SettingsDialog.awaitDismissed()

            assertTrue(
                "the required action survives into the stored selection",
                ConfigurableAction.EXIT_FULLSCREEN.id in
                    SettingsPrefs.stringSet(Preferences.fullScreenOverlayActionsKey).orEmpty(),
            )
            assertEquals(
                "the required action leads the stored order",
                ConfigurableAction.EXIT_FULLSCREEN.id,
                SettingsPrefs.orderList(Preferences.fullScreenOverlayActionOrderKey).first(),
            )
        }
    }

    @Test
    @TestId("SET-44")
    @TestDuration(DurationCategory.NORMAL)
    fun theFullscreenOverlayCanBeFilledOrEmptiedAndKeepsItsRequiredAction() {
        launchSettingsOn(SettingsPage.CUSTOMIZE_CONTROLS).use {
            openRowDialog(fullScreenTitle)
            val everyLabel = SettingsDialog.allRowLabelsInOrder().toSet()
            SettingsDialog.checkOnlyRecyclerRows(everyLabel)
            SettingsDialog.apply()
            SettingsDialog.awaitDismissed()

            SettingsPrefs.awaitStringSet(
                Preferences.fullScreenOverlayActionsKey,
                ConfigurableAction.fullScreenOverlayActions.map { it.id }.toSet() +
                    ConfigurableAction.EXIT_FULLSCREEN.id,
            )

            openRowDialog(fullScreenTitle)
            SettingsDialog.checkOnlyRecyclerRows(emptySet())
            SettingsDialog.apply()
            SettingsDialog.awaitDismissed()

            SettingsPrefs.awaitStringSet(
                Preferences.fullScreenOverlayActionsKey,
                setOf(ConfigurableAction.EXIT_FULLSCREEN.id),
            )
        }
    }

    private fun openRowDialog(rowTitle: String) {
        SettingsScreen.tapRow(rowTitle)
        SettingsDialog.awaitShown()
        SettingsDialog.awaitRowList()
    }

    private fun label(action: ConfigurableAction): String = SettingsStrings.of(action.titleRes)

    private fun idOfLabel(label: String): String =
        ConfigurableAction.entries.first { SettingsStrings.of(it.titleRes) == label }.id

    private val primaryTitle: String get() = SettingsStrings.of(R.string.primary_button_action)
    private val secondaryTitle: String get() = SettingsStrings.of(R.string.secondary_button_action)
    private val shortcutTitle: String get() = SettingsStrings.of(R.string.shortcut_bar_buttons)
    private val fullScreenTitle: String get() = SettingsStrings.of(R.string.fullscreen_buttons)
}
