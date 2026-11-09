// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.actions

import org.junit.Assert.assertEquals
import org.junit.Test

class ConfigurableActionSelectionsTest {

    @Test
    fun savedOverlayOrderMovesExitFirstAndAppendsMissingDefaults() {
        val saved = listOf("toggle_labels", "exit_fullscreen", "bogus")
        val expected = listOf("exit_fullscreen", "toggle_labels") +
            ConfigurableAction.defaultFullScreenOverlayOrder.map { it.id }
                .filterNot { it in setOf("exit_fullscreen", "toggle_labels") }

        assertEquals(expected, orderedFullScreenOverlayActionIds(saved))
    }

    @Test
    fun shortcutBarRejectsExitFullscreenInSelectionsAndOrder() {
        assertEquals(setOf("search"), selectedShortcutBarActionIds(setOf("exit_fullscreen", "search")))
        assertEquals(
            ConfigurableAction.defaultShortcutBarOrder.map { it.id },
            orderedShortcutBarActionIds(listOf("exit_fullscreen")),
        )
    }

    @Test
    fun overlayAlwaysSelectsExitFullscreenAndRejectsUnsupportedActions() {
        assertEquals(setOf("exit_fullscreen"), selectedFullScreenOverlayActionIds(emptySet()))
        assertEquals(
            setOf("exit_fullscreen", "search"),
            selectedFullScreenOverlayActionIds(setOf("search", "settings", "bogus", "none")),
        )
    }

    @Test
    fun shortcutBarFilteringPreservesAllowedOrderAndDuplicates() {
        assertEquals(
            listOf("settings", "search", "settings", "table_of_contents"),
            filteredShortcutBarActionIds(
                listOf(
                    "settings", "bogus", "exit_fullscreen", "search",
                    "brightness", "settings", "none", "table_of_contents",
                ),
            ),
        )
    }

    @Test
    fun emptySavedOrdersUseTheSurfaceDefaults() {
        assertEquals(
            ConfigurableAction.defaultFullScreenOverlayOrder.map { it.id },
            orderedFullScreenOverlayActionIds(emptyList()),
        )
        assertEquals(
            ConfigurableAction.defaultShortcutBarOrder.map { it.id },
            orderedShortcutBarActionIds(emptyList()),
        )
    }

    @Test
    fun savedDefaultOrdersKeepTheirOrderWithoutDuplicates() {
        val overlayDefaults = ConfigurableAction.defaultFullScreenOverlayOrder.map { it.id }
        val shortcutDefaults = ConfigurableAction.defaultShortcutBarOrder.map { it.id }

        assertEquals(overlayDefaults, orderedFullScreenOverlayActionIds(overlayDefaults))
        assertEquals(shortcutDefaults, orderedShortcutBarActionIds(shortcutDefaults))
    }

    @Test
    fun savedShortcutOrderKeepsFirstOccurrencesAndAppendsMissingDefaults() {
        val saved = listOf("settings", "search", "settings", "bogus")
        val expected = listOf("settings", "search") +
            ConfigurableAction.defaultShortcutBarOrder.map { it.id }
                .filterNot { it in setOf("settings", "search") }

        assertEquals(expected, orderedShortcutBarActionIds(saved))
    }

    @Test
    fun selectedOverlayActionsKeepExitFirstAndFollowMergedOrder() {
        assertEquals(
            listOf(ConfigurableAction.EXIT_FULLSCREEN, ConfigurableAction.TOGGLE_LABELS, ConfigurableAction.SEARCH),
            orderedSelectedFullScreenOverlayActions(
                setOf("search", "toggle_labels", "settings", "bogus"),
                listOf("toggle_labels", "rotate", "bogus"),
            ),
        )
    }

    @Test
    fun selectedShortcutActionsFollowMergedOrderAndExcludeExitFullscreen() {
        assertEquals(
            listOf(ConfigurableAction.SETTINGS, ConfigurableAction.SEARCH),
            orderedSelectedShortcutBarActions(
                setOf("search", "exit_fullscreen", "settings", "bogus"),
                listOf("settings", "rotate", "exit_fullscreen", "bogus"),
            ),
        )
    }
}
