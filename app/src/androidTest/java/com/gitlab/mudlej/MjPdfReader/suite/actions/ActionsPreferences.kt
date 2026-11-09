// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.actions

import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.reader.actions.ConfigurableAction

object ActionsPreferences {

    val defaultShortcutBarActions: List<ConfigurableAction>
        get() = ConfigurableAction.defaultShortcutBarSelectedActions

    val defaultShortcutBarOrder: List<ConfigurableAction>
        get() = ConfigurableAction.defaultShortcutBarOrder

    val defaultFullScreenOverlayActionIds: Set<String>
        get() = ConfigurableAction.defaultFullScreenOverlayActionIds

    val defaultFullScreenOverlayOrder: List<ConfigurableAction>
        get() = ConfigurableAction.defaultFullScreenOverlayOrder

    fun seedPrimaryAction(action: ConfigurableAction) = seedPrimaryActionId(action.id)

    fun seedPrimaryActionId(actionId: String) =
        SettingsSeeder.seed(Preferences.primaryButtonActionKey, actionId)

    fun seedLegacyReloadButton(enabled: Boolean) =
        SettingsSeeder.seed(Preferences.enableReloadButtonKey, enabled)

    fun seedShortcutBarEnabled(enabled: Boolean) =
        SettingsSeeder.seed(Preferences.secondBarEnabledKey, enabled)

    fun seedShortcutBar(
        selected: List<ConfigurableAction>,
        order: List<ConfigurableAction> = selected,
        enabled: Boolean = true,
    ) {
        SettingsSeeder.seedAll(
            mapOf(
                Preferences.shortcutBarActionsKey to selected.map { it.id }.toSet(),
                Preferences.shortcutBarActionOrderKey to order.joinToString(SEPARATOR) { it.id },
                Preferences.secondBarEnabledKey to enabled,
            )
        )
    }

    fun seedEmptyShortcutBar(enabled: Boolean = true) {
        SettingsSeeder.seedAll(
            mapOf(
                Preferences.shortcutBarActionsKey to emptySet<String>(),
                Preferences.secondBarEnabledKey to enabled,
            )
        )
    }

    fun seedFullScreenOverlay(
        selected: List<ConfigurableAction>,
        order: List<ConfigurableAction> = selected,
    ) {
        SettingsSeeder.seedAll(
            mapOf(
                Preferences.fullScreenOverlayActionsKey to selected.map { it.id }.toSet(),
                Preferences.fullScreenOverlayActionOrderKey to order.joinToString(SEPARATOR) { it.id },
            )
        )
    }

    fun primaryActionId(): String? = string(Preferences.primaryButtonActionKey)

    fun shortcutBarActionIds(): Set<String>? = stringSet(Preferences.shortcutBarActionsKey)

    fun shortcutBarOrderIds(): List<String>? = orderList(Preferences.shortcutBarActionOrderKey)

    fun fullScreenOverlayActionIds(): Set<String>? = stringSet(Preferences.fullScreenOverlayActionsKey)

    fun shortcutBarEnabled(): Boolean? = SettingsSeeder.preferences.all[Preferences.secondBarEnabledKey] as? Boolean

    fun contains(key: String): Boolean = SettingsSeeder.preferences.contains(key)

    fun assertUnset(key: String) {
        if (contains(key)) {
            throw AssertionError(
                "Preference $key holds ${SettingsSeeder.preferences.all[key]} while the test requires it to have " +
                    "never been written. A default that is read through a fallback branch reads differently once " +
                    "the key exists, so an unwritten key and a key written to its default are not the same state."
            )
        }
    }

    fun awaitString(key: String, expected: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            "preference $key reads \"$expected\"",
            timeoutMs,
            read = { string(key) },
        ) { it == expected }
    }

    fun awaitBoolean(key: String, expected: Boolean, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            "preference $key reads $expected",
            timeoutMs,
            read = { SettingsSeeder.preferences.all[key] },
        ) { it == expected }
    }

    fun awaitOrderStartsWith(key: String, expected: List<String>, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            "preference $key begins with $expected",
            timeoutMs,
            read = { orderList(key) },
        ) { it != null && it.size >= expected.size && it.subList(0, expected.size) == expected }
    }

    fun ids(actions: List<ConfigurableAction>): List<String> = actions.map { it.id }

    fun idSet(actions: List<ConfigurableAction>): Set<String> = actions.map { it.id }.toSet()

    private fun string(key: String): String? = SettingsSeeder.preferences.all[key] as? String

    private fun stringSet(key: String): Set<String>? {
        @Suppress("UNCHECKED_CAST")
        return SettingsSeeder.preferences.all[key] as? Set<String>
    }

    private fun orderList(key: String): List<String>? =
        string(key)?.split(SEPARATOR)?.filter { it.isNotBlank() }

    const val SEPARATOR = ","

    const val DEFAULT_SHORTCUT_BAR_SIZE = 7
}
