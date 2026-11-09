// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.robot

import android.graphics.Point
import android.view.accessibility.AccessibilityEvent
import androidx.test.uiautomator.EventCondition
import android.content.ClipData
import android.content.ClipboardManager
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import java.util.regex.Pattern

class SafPickerRobot(
    private val uptimeMillis: () -> Long = { SystemClock.uptimeMillis() },
) : Robot() {

    override fun awaitShown(timeoutMs: Long) {
        Await.untilNotNull(
            "one of $DOCUMENTS_UI_PACKAGES is foreground",
            timeoutMs,
        ) { activePackageName() }
    }

    fun openDrawer(): Boolean {
        val roots = By.res(requirePackageName(), ROOTS_LIST_ID)
        if (device.hasObject(roots)) return true
        repeat(STALE_RETRIES) {
            val handle = SHOW_ROOTS_DESCRIPTIONS.firstNotNullOfOrNull {
                find(By.desc(it), Await.SHORT_TIMEOUT_MS / SHOW_ROOTS_DESCRIPTIONS.size + 1)
            } ?: return false
            if (runCatching { PickerClicks.click(handle) }.getOrDefault(false) &&
                device.wait(Until.hasObject(roots), Await.SHORT_TIMEOUT_MS) == true
            ) {
                waitForIdle()
                return true
            }
            waitForIdle()
        }
        return false
    }

    fun openFolder(name: String): SafPickerRobot {
        awaitShown()
        val listEntry = By.text(name).hasAncestor(
            By.res(requirePackageName(), DIRECTORY_LIST_ID)
        )
        repeat(STALE_RETRIES) {
            if (currentRootTitle() == name) return this
            val entry = scrollToEntry(listEntry, Await.SHORT_TIMEOUT_MS)
                ?: throw ConditionTimeoutException(
                    "The picker shows no folder entry named $name, visible entries are ${visibleTexts()}"
                )
            runCatching { entry.click() }
            waitForIdle()
            if (
                currentRootTitle() == name ||
                find(listEntry, Await.SHORT_TIMEOUT_MS) == null
            ) return this
        }
        throw ConditionTimeoutException(
            "The picker did not enter folder $name, visible entries are ${visibleTexts()}"
        )
    }

    fun scrollToEntry(
        selector: BySelector,
        timeoutMs: Long = Await.SHORT_TIMEOUT_MS,
        targetText: String? = null,
    ): UiObject2? {
        val configuration = Configurator.getInstance()
        val previousIdleTimeout = configuration.waitForIdleTimeout
        configuration.waitForIdleTimeout = 0L
        return try {
            val deadline = uptimeMillis() + timeoutMs
            findBeforeDeadline(selector, deadline)?.let { return it }

            val covered = device.findObject(selector)
            val firstDirection = if (covered != null && covered.visibleBounds.top < pickerContentTop()) {
                Direction.UP
            } else {
                targetText?.let(::directionToward) ?: Direction.DOWN
            }
            scrollToEntry(selector, firstDirection, deadline)?.let { return it }
            if (uptimeMillis() >= deadline) return null
            val secondDirection = if (firstDirection == Direction.DOWN) Direction.UP else Direction.DOWN
            scrollToEntry(selector, secondDirection, deadline)
        } finally {
            configuration.waitForIdleTimeout = previousIdleTimeout
        }
    }

    private fun scrollToEntry(
        selector: BySelector,
        direction: Direction,
        deadline: Long,
    ): UiObject2? {
        for (step in 0 until SCROLL_STEPS) {
            if (uptimeMillis() >= deadline) return null
            val canContinue = scrollPicker(direction)
            device.findObject(selector)?.takeIf(::isExposedEntry)?.let { return it }
            if (!canContinue) break
        }
        return findBeforeDeadline(selector, deadline)
    }

    private fun directionToward(targetText: String): Direction {
        val extension = targetText.substringAfterLast('.', missingDelimiterValue = "")
        val suffix = extension.takeIf { it.isNotEmpty() }?.let { ".$it" }
        val packageName = requirePackageName()
        val visibleFileNames = device.findObject(By.res(packageName, DIRECTORY_LIST_ID))
            ?.findObjects(By.res(FILE_NAME_FIELD_ID))
            ?.filter {
                !it.visibleBounds.isEmpty &&
                    it.parent?.parent?.let { row ->
                        listOf("metadata", "file_type", "details", "size").any { metadataId ->
                            row.findObject(By.res(packageName, metadataId)) != null
                        }
                    } == true
            }
            ?.mapNotNull { it.text }.orEmpty()
        val firstVisibleName = suffix?.let { wantedSuffix ->
            visibleFileNames.firstOrNull { it.endsWith(wantedSuffix, ignoreCase = true) }
        } ?: visibleFileNames.firstOrNull()
        return if (
            firstVisibleName == null ||
            targetText.compareTo(firstVisibleName, ignoreCase = true) >= 0
        ) {
            Direction.DOWN
        } else {
            Direction.UP
        }
    }

    private fun scrollPicker(direction: Direction): Boolean {
        val packageName = requirePackageName()
        val selector = By.res(packageName, DIRECTORY_LIST_ID)
        val list = device.findObject(selector) ?: return false
        val original = list.visibleBounds
        val before = list.findObjects(By.res(FILE_NAME_FIELD_ID)).map { "${it.text}:${it.visibleBounds}" }
        val bounds = PickerClicks.touchableBounds(list) ?: return false
        bounds.top = maxOf(bounds.top, pickerContentTop())
        if (bounds.isEmpty) return false
        val margin = (bounds.height() * SCROLL_EDGE_FRACTION).toInt()
        val distance = (bounds.height() * SCROLL_FRACTION).toInt()
        val startY = if (direction == Direction.DOWN) bounds.bottom - margin else bounds.top + margin
        val endY = if (direction == Direction.DOWN) startY - distance else startY + distance
        val start = Point(bounds.centerX(), startY)
        val end = Point(bounds.centerX(), endY)
        val scrollEvent = object : EventCondition<Boolean>() {
            private var observed = false

            override fun accept(event: AccessibilityEvent): Boolean {
                if (event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED ||
                    event.packageName?.toString() != packageName) return false
                observed = true
                return true
            }

            override fun getResult(): Boolean = observed
        }
        val acknowledged = device.performActionAndWait({
            check(device.swipe(arrayOf(start, end, end), SCROLL_SEGMENT_STEPS)) {
                "The picker scrolling gesture was not injected"
            }
        }, scrollEvent, SCROLL_ACK_TIMEOUT_MS)
        val refreshed = device.findObject(selector) ?: return false
        return acknowledged == true || refreshed.visibleBounds != original ||
            refreshed.findObjects(By.res(FILE_NAME_FIELD_ID)).map { "${it.text}:${it.visibleBounds}" } != before
    }

    private fun pickerContentTop(): Int {
        val packageName = requirePackageName()
        return listOf(TOOLBAR_ID, "breadcrumb_text", "header_title")
            .mapNotNull { device.findObject(By.res(packageName, it))?.visibleBounds?.bottom }
            .maxOrNull() ?: 0
    }

    private fun isExposedEntry(node: UiObject2): Boolean {
        val bounds = node.visibleBounds
        if (bounds.isEmpty) return false
        val directoryListId = "${requirePackageName()}:id/$DIRECTORY_LIST_ID"
        var ancestor = node.parent
        while (ancestor != null) {
            if (ancestor.resourceName == directoryListId) return bounds.top >= pickerContentTop()
            ancestor = ancestor.parent
        }
        return true
    }

    private fun findBeforeDeadline(selector: BySelector, deadline: Long): UiObject2? {
        val remainingMs = deadline - uptimeMillis()
        if (remainingMs <= 0L) return null
        return find(selector, minOf(SCROLL_FIND_TIMEOUT_MS, remainingMs))?.takeIf(::isExposedEntry)
    }

    private fun clickWithRetries(selector: BySelector, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS, describe: () -> String) {
        repeat(STALE_RETRIES) {
            val entry = scrollToEntry(selector, timeoutMs) ?: throw ConditionTimeoutException(describe())
            if (runCatching { check(PickerClicks.click(entry)) { "The picker entry has no exposed touch point" } }.isSuccess) {
                waitForIdle()
                return
            }
            waitForIdle()
        }
        throw ConditionTimeoutException(
            "The picker node matching $selector went stale on every one of $STALE_RETRIES attempts to click it. " +
                describe()
        )
    }

    fun navigateToRoot(vararg titles: String): SafPickerRobot {
        awaitShown()
        val wanted = titles.toList()
        repeat(NAVIGATION_ATTEMPTS) {
            if (currentRootTitle() in wanted) return this
            if (openDrawer()) {
                settleDrawer()
                wanted.forEach { title ->
                    val rootsListEntry = By.text(title).hasAncestor(
                        By.res(requirePackageName(), ROOTS_LIST_ID),
                    )
                    val rootEntry = if (device.hasObject(rootsListEntry)) {
                        rootsListEntry
                    } else {
                        By.text(title).hasAncestor(By.clickable(true))
                    }
                    val selected = runCatching {
                        clickWithRetries(rootEntry, Await.SHORT_TIMEOUT_MS) {
                            "The drawer stopped offering the root named $title"
                        }
                    }.isSuccess
                    if (selected && awaitRootTitle(wanted)) return this
                }
                if (device.hasObject(By.res(requirePackageName(), ROOTS_LIST_ID))) {
                    device.pressBack()
                    waitForIdle()
                }
            }
            wanted.forEach { title ->
                val directoryEntry = By.text(title).hasAncestor(
                    By.res(requirePackageName(), DIRECTORY_LIST_ID),
                )
                if (find(directoryEntry, Await.SHORT_TIMEOUT_MS) != null) {
                    openFolder(title)
                    if (awaitRootTitle(wanted)) return this
                }
            }
        }
        throw ConditionTimeoutException(
            "After $NAVIGATION_ATTEMPTS attempts the picker never settled on any of $wanted. Its toolbar reads " +
                "\"${currentRootTitle()}\" and its visible entries are ${visibleTexts()}"
        )
    }

    fun currentRootTitle(): String? {
        val toolbar = device.findObject(By.res(requirePackageName(), TOOLBAR_ID)) ?: return null
        return runCatching { toolbar.findObject(By.clazz(TEXT_VIEW_CLASS))?.text }.getOrNull()
    }

    private fun awaitRootTitle(wanted: List<String>): Boolean {
        val deadline = System.currentTimeMillis() + Await.SHORT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (currentRootTitle() in wanted) return true
            Thread.sleep(DRAWER_SETTLE_MS)
        }
        return false
    }

    private fun settleDrawer() {
        waitForIdle()
        Thread.sleep(DRAWER_SETTLE_MS)
        waitForIdle()
    }

    fun navigateToDownloads(): SafPickerRobot {
        awaitShown()
        if (currentRootTitle() in listOf(DOWNLOADS_ROOT_TITLE, DOWNLOAD_DIRECTORY_NAME)) return this
        val downloadEntry = By.text(DOWNLOAD_DIRECTORY_NAME).hasAncestor(
            By.res(requirePackageName(), DIRECTORY_LIST_ID),
        )
        if (find(downloadEntry, Await.SHORT_TIMEOUT_MS) != null) {
            openFolder(DOWNLOAD_DIRECTORY_NAME)
            return this
        }
        return navigateToRoot(DOWNLOADS_ROOT_TITLE, DOWNLOAD_DIRECTORY_NAME)
    }

    fun navigateToDocuments(): SafPickerRobot {
        return navigateToRoot(DOCUMENTS_TITLE)
    }

    fun showListView(): SafPickerRobot {
        val packageName = requirePackageName()
        val switch = device.findObject(By.res(packageName, "sub_menu_list")) ?: return this
        check(PickerClicks.click(switch)) { "The picker list-view control has no exposed touch point" }
        Await.until("the picker switches to list view", Await.SHORT_TIMEOUT_MS) {
            device.hasObject(By.res(packageName, "sub_menu_grid"))
        }
        return this
    }


    fun visibleTexts(): List<String> =
        device.findObjects(By.pkg(requirePackageName())).mapNotNull { it.text }.filter { it.isNotBlank() }.distinct()

    fun chooseFileNamed(
        name: String,
        timeoutMs: Long = Await.LONG_TIMEOUT_MS,
        onSelectionDispatched: () -> Unit = {},
    ): SafPickerRobot = chooseExactFileNamed(
        name,
        timeoutMs,
        allowScroll = true,
        onSelectionDispatched = onSelectionDispatched,
    )

    fun chooseVisibleFileNamed(
        name: String,
        timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS,
        onSelectionDispatched: () -> Unit = {},
    ): SafPickerRobot = chooseExactFileNamed(
        name,
        timeoutMs,
        allowScroll = false,
        onSelectionDispatched = onSelectionDispatched,
    )

    fun chooseFileNamedInCurrentFolder(
        name: String,
        timeoutMs: Long = Await.LONG_TIMEOUT_MS,
        onSelectionDispatched: () -> Unit = {},
    ): SafPickerRobot = chooseExactFileNamed(
        name,
        timeoutMs,
        allowScroll = true,
        filterByName = false,
        onSelectionDispatched = onSelectionDispatched,
    )

    private fun chooseExactFileNamed(
        name: String,
        timeoutMs: Long,
        allowScroll: Boolean,
        filterByName: Boolean = true,
        onSelectionDispatched: () -> Unit,
    ): SafPickerRobot {
        val deadline = uptimeMillis() + timeoutMs
        awaitShown(timeoutMs)
        val exactName = By.text(name).hasAncestor(
            By.res(requirePackageName(), DIRECTORY_LIST_ID),
        )
        var searchActive = false
        if (allowScroll && filterByName) {
            for (attempt in 0 until STALE_RETRIES) {
                try {
                    searchActive = filterPickerByName(name, exactName, deadline)
                    break
                } catch (_: StaleObjectException) {
                }
            }
        }
        repeat(STALE_RETRIES) {
            val remainingMs = deadline - uptimeMillis()
            if (remainingMs <= 0L) {
                throw ConditionTimeoutException(fileDiagnostic(name, allowScroll))
            }
            try {
                if (allowScroll) {
                    if (searchActive) {
                        find(exactName, minOf(Await.DEFAULT_TIMEOUT_MS, remainingMs))
                            ?: scrollToEntry(exactName, remainingMs, targetText = name)
                    } else {
                        scrollToEntry(exactName, remainingMs, targetText = name)
                    }
                } else {
                    find(exactName, remainingMs)
                } ?: throw ConditionTimeoutException(fileDiagnostic(name, allowScroll))
                waitForIdle()
                val readyNode = device.findObject(exactName)?.takeIf(::isExposedEntry) ?: return@repeat
                check(PickerClicks.click(readyNode)) { "The picker row named $name has no exposed touch point" }
                onSelectionDispatched()

                awaitPickerDismissed(timeoutMs)
                return this
            } catch (_: StaleObjectException) {
            }
        }
        throw ConditionTimeoutException(
            "The exact picker row named $name went stale on every one of $STALE_RETRIES attempts. " +
                fileDiagnostic(name, allowScroll),
        )
    }


    private fun filterPickerByName(
        name: String,
        exactName: BySelector,
        deadline: Long,
    ): Boolean {
        var remainingMs = deadline - uptimeMillis()
        if (remainingMs <= 0L) return false
        val search = find(
            By.desc(SEARCH_DESCRIPTION),
            minOf(Await.SHORT_TIMEOUT_MS, remainingMs),
        ) ?: return false
        search.click()
        waitForIdle()
        device.findObject(By.desc(CLEAR_QUERY_DESCRIPTION))?.let {
            it.click()
            waitForIdle()
        }

        remainingMs = deadline - uptimeMillis()
        if (remainingMs <= 0L) return false
        val fieldSelector = By.pkg(requirePackageName()).res(Pattern.compile(".*:id/$SEARCH_FIELD_ID"))
        val field = find(
            fieldSelector,
            minOf(Await.SHORT_TIMEOUT_MS, remainingMs),
        ) ?: return false
        field.click()
        val clipboard = TestEnvironment.targetContext.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("picker file name", name))
        check(device.pressKeyCode(KeyEvent.KEYCODE_V, KeyEvent.META_CTRL_ON)) {
            "UiDevice refused Ctrl+V while replacing the picker search query"
        }
        waitForIdle()
        val queryAccepted = field.text == name || field.text.startsWith("$name ")

        if (queryAccepted && TestEnvironment.instrumentation.uiAutomation.windows.any {
            it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
        }) {
            device.pressBack()
            waitForIdle()
        }
        remainingMs = deadline - uptimeMillis()
        if (queryAccepted && remainingMs > 0L &&
            scrollToEntry(exactName, remainingMs, targetText = name) != null
        ) {
            return true
        }

        repeat(SEARCH_EXIT_BACK_PRESSES) {
            device.pressBack()
            waitForIdle()
            if (!device.hasObject(fieldSelector)) return false
        }
        return false
    }

    private fun visibleFileDiagnostic(name: String): String =
        "The picker shows no visible entry named $name in root \"${currentRootTitle()}\", " +
            "visible entries are ${visibleTexts()}"

    private fun fileDiagnostic(name: String, allowScroll: Boolean): String =
        if (allowScroll) {
            "The picker shows no entry named $name, visible entries are ${visibleTexts()}"
        } else {
            visibleFileDiagnostic(name)
        }

    fun confirmUseThisFolder(): SafPickerRobot {
        awaitShown()
        val confirm = findAnyText(USE_THIS_FOLDER_LABELS, Await.DEFAULT_TIMEOUT_MS)
            ?: throw ConditionTimeoutException(
                "The picker shows no folder confirmation button, tried $USE_THIS_FOLDER_LABELS"
            )
        confirm.click()
        allowAccessIfPrompted()
        awaitPickerDismissed()
        return this
    }

    fun typeFileNameAndSave(name: String): SafPickerRobot {
        awaitShown()
        val packageName = requirePackageName()
        val field = find(fileNameFieldSelector(), Await.DEFAULT_TIMEOUT_MS)
            ?: find(By.pkg(packageName).clazz(EDIT_TEXT_CLASS), Await.SHORT_TIMEOUT_MS)
            ?: throw ConditionTimeoutException(
                "The create document picker shows no file name field. Tried the framework id " +
                    "$FILE_NAME_FIELD_ID and any EditText in $packageName"
            )
        field.text = name
        Await.until("the create picker file name field reads back as $name", Await.DEFAULT_TIMEOUT_MS) {
            runCatching { field.text }.getOrNull() == name
        }
        val save = findAnyText(SAVE_LABELS, Await.SHORT_TIMEOUT_MS)
            ?: throw ConditionTimeoutException("The create document picker shows no save button")
        save.click()
        awaitPickerDismissed()
        return this
    }

    fun allowAccessIfPrompted(): SafPickerRobot {
        findAnyText(ALLOW_LABELS, Await.SHORT_TIMEOUT_MS)?.click()
        waitForIdle()
        return this
    }

    fun cancel(): SafPickerRobot {
        repeat(MAX_BACK_PRESSES) {
            if (!isShowing()) return this
            device.pressBack()
            waitForIdle()
        }
        awaitPickerDismissed(Await.SHORT_TIMEOUT_MS)
        return this
    }

    fun awaitPickerDismissed(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until(
            "the system document picker is dismissed",
            timeoutMs,
        ) { !isShowing() }
    }

    fun isShowing(): Boolean = activePackageName() != null

    fun hasFolderConfirmationButton(): Boolean =
        findAnyText(USE_THIS_FOLDER_LABELS, Await.SHORT_TIMEOUT_MS) != null

    fun activePackageName(): String? =
        DOCUMENTS_UI_PACKAGES.firstOrNull { device.hasObject(By.pkg(it)) }

    private fun requirePackageName(): String =
        activePackageName()
            ?: throw ConditionTimeoutException(
                "None of $DOCUMENTS_UI_PACKAGES is foreground, foreground is ${device.currentPackageName}"
            )

    private fun fileNameFieldSelector(): BySelector =
        By.pkg(requirePackageName()).res(FILE_NAME_FIELD_ID).clazz(EDIT_TEXT_CLASS)

    private fun find(selector: BySelector, timeoutMs: Long): UiObject2? =
        device.wait(Until.findObject(selector), timeoutMs)

    private fun findAnyText(labels: List<String>, timeoutMs: Long): UiObject2? {
        labels.forEach { label ->
            find(By.text(label), timeoutMs / labels.size + 1)?.let { return it }
            find(By.textContains(label), 1)?.let { return it }
        }
        return null
    }

    companion object {
        val DOCUMENTS_UI_PACKAGES = listOf("com.google.android.documentsui", "com.android.documentsui")
        const val FILE_NAME_FIELD_ID = "android:id/title"
        const val EDIT_TEXT_CLASS = "android.widget.EditText"
        const val DOCUMENTS_TITLE = "Documents"
        const val MAX_BACK_PRESSES = 6
        const val STALE_RETRIES = 3
        const val NAVIGATION_ATTEMPTS = 3
        const val DRAWER_SETTLE_MS = 250L
        const val TOOLBAR_ID = "toolbar"
        const val ROOTS_LIST_ID = "roots_list"
        const val TEXT_VIEW_CLASS = "android.widget.TextView"
        const val DIRECTORY_LIST_ID = "dir_list"
        const val SCROLL_STEPS = 60
        const val SCROLL_FRACTION = 0.8f
        const val SCROLL_EDGE_FRACTION = 0.05f
        const val SCROLL_SEGMENT_STEPS = 10
        const val SCROLL_ACK_TIMEOUT_MS = 1_000L
        const val SCROLL_FIND_TIMEOUT_MS = 250L
        const val SEARCH_DESCRIPTION = "Search"
        const val SEARCH_FIELD_ID = "search_src_text"
        const val CLEAR_QUERY_DESCRIPTION = "Clear query"
        const val SEARCH_EXIT_BACK_PRESSES = 3

        val SHOW_ROOTS_DESCRIPTIONS = listOf("Show roots", "Show roots and devices", "Open navigation drawer")
        const val DOWNLOADS_ROOT_TITLE = "Downloads"
        const val DOWNLOAD_DIRECTORY_NAME = "Download"

        val USE_THIS_FOLDER_LABELS = listOf("USE THIS FOLDER", "Use this folder", "SELECT", "Select")
        val ALLOW_LABELS = listOf("ALLOW", "Allow")
        val SAVE_LABELS = listOf("SAVE", "Save")
    }
}
