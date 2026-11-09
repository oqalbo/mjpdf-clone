// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.actions

fun selectedFullScreenOverlayActionIds(actionIds: Set<String>): Set<String> {
    return filteredFullScreenOverlayActionIds(actionIds).toSet() + ConfigurableAction.requiredFullScreenOverlayActionIds
}

fun orderedSelectedFullScreenOverlayActions(
    selectedIds: Set<String>,
    actionOrder: List<String>,
): List<ConfigurableAction> {
    val visibleIds = selectedFullScreenOverlayActionIds(selectedIds)
    return orderedFullScreenOverlayActionIds(actionOrder)
        .filter { visibleIds.contains(it) }
        .map(ConfigurableAction::fromId)
}

fun orderedFullScreenOverlayActions(actionOrder: List<String>): List<ConfigurableAction> {
    return orderedFullScreenOverlayActionIds(actionOrder).map(ConfigurableAction::fromId)
}

fun orderedFullScreenOverlayActionIds(actionOrder: List<String>): List<String> {
    val defaultOrder = ConfigurableAction.defaultFullScreenOverlayOrder.map { it.id }
    return (actionOrder + defaultOrder)
        .distinct()
        .filterFullScreenOverlayActionIds()
        .ensureRequiredFullScreenActionsFirst()
}

fun selectedShortcutBarActionIds(actionIds: Set<String>): Set<String> {
    return filteredShortcutBarActionIds(actionIds).toSet()
}

fun orderedSelectedShortcutBarActions(
    selectedIds: Set<String>,
    actionOrder: List<String>,
): List<ConfigurableAction> {
    val visibleIds = selectedShortcutBarActionIds(selectedIds)
    return orderedShortcutBarActionIds(actionOrder)
        .filter { visibleIds.contains(it) }
        .map(ConfigurableAction::fromId)
}

fun orderedShortcutBarActions(actionOrder: List<String>): List<ConfigurableAction> {
    return orderedShortcutBarActionIds(actionOrder).map(ConfigurableAction::fromId)
}

fun orderedShortcutBarActionIds(actionOrder: List<String>): List<String> {
    val defaultOrder = ConfigurableAction.defaultShortcutBarOrder.map { it.id }
    return (actionOrder + defaultOrder)
        .distinct()
        .filterShortcutBarActionIds()
}

fun filteredShortcutBarActionIds(actionIds: Iterable<String>): List<String> {
    return actionIds.filter { shortcutBarActionIdSet.contains(it) }
}

private fun filteredFullScreenOverlayActionIds(actionIds: Iterable<String>): List<String> {
    return actionIds.filter { fullScreenOverlayActionIdSet.contains(it) }
}

private fun Iterable<String>.filterFullScreenOverlayActionIds(): List<String> {
    return filter { fullScreenOverlayActionIdSet.contains(it) }
}

private fun Iterable<String>.filterShortcutBarActionIds(): List<String> {
    return filter { shortcutBarActionIdSet.contains(it) }
}

private fun List<String>.ensureRequiredFullScreenActionsFirst(): List<String> {
    return ConfigurableAction.requiredFullScreenOverlayActionIds.toList() + filterNot {
        ConfigurableAction.requiredFullScreenOverlayActionIds.contains(it)
    }
}

private val fullScreenOverlayActionIdSet: Set<String>
    get() = (listOf(ConfigurableAction.EXIT_FULLSCREEN) + ConfigurableAction.fullScreenOverlayActions)
        .map { it.id }
        .toSet()

private val shortcutBarActionIdSet: Set<String>
    get() = ConfigurableAction.shortcutBarActions.map { it.id }.toSet()
