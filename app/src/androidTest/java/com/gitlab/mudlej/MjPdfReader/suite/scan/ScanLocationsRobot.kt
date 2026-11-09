// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.scan

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.robot.Robot
import com.gitlab.mudlej.MjPdfReader.suite.robot.Taps
import com.gitlab.mudlej.MjPdfReader.ui.home.BreadcrumbAdapter
import com.gitlab.mudlej.MjPdfReader.ui.home.ScanLocationRow
import com.gitlab.mudlej.MjPdfReader.ui.home.ScanLocationsActivity
import com.gitlab.mudlej.MjPdfReader.ui.home.ScanLocationsAdapter
import com.gitlab.mudlej.MjPdfReader.ui.home.StorageManager
import com.google.android.material.checkbox.MaterialCheckBox
import java.io.File

class ScanLocationsRobot : Robot() {

    override fun awaitShown(timeoutMs: Long) {
        Screens.awaitResumed(ScanLocationsActivity::class.java, timeoutMs)
        Await.until("the scan locations list is attached", timeoutMs) { recyclerOrNull() != null }
        awaitRowsSettled(timeoutMs)
    }

    fun isShowing(): Boolean =
        TestEnvironment.resumedActivityOfType(ScanLocationsActivity::class.java) != null

    fun awaitFinished(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Screens.awaitGone(ScanLocationsActivity::class.java, timeoutMs)
    }

    fun rows(): List<ScanLocationRow> = TestEnvironment.onMainThread {
        locationsAdapter()?.currentList?.toList().orEmpty()
    }

    fun rowNames(): List<String> = rows().map { it.name }

    fun folderNames(): List<String> = rows().filterNot { it.isUp }.map { it.name }

    fun hasRow(name: String): Boolean = rowNames().contains(name)

    fun hasUpRow(): Boolean = rows().any { it.isUp }

    fun row(name: String): ScanLocationRow = rows().firstOrNull { it.name == name }
        ?: throw AssertionError(
            "The scan locations picker shows no row named \"$name\" at ${crumbs()}. It shows: ${rowNames()}"
        )

    fun checkedStateOf(name: String): Int = row(name).checkedState

    fun isCheckboxEnabled(name: String): Boolean = row(name).checkboxEnabled

    fun crumbs(): List<String> = TestEnvironment.onMainThread {
        crumbViews().map { it.text.toString() }
    }

    fun awaitCrumbs(expected: List<String>, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): List<String> =
        Await.untilValue(
            "the scan locations breadcrumb reads $expected",
            timeoutMs,
            read = { crumbs() },
        ) { it == expected }

    fun awaitRow(name: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): ScanLocationRow {
        Await.until("the scan locations picker lists a row named \"$name\"", timeoutMs) { hasRow(name) }
        return row(name)
    }

    fun awaitCheckedState(name: String, expected: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            "the row \"$name\" reports checked state ${stateName(expected)}",
            timeoutMs,
            read = { rows().firstOrNull { it.name == name }?.checkedState },
        ) { it == expected }
    }

    private fun tapRow(name: String) {
        awaitRow(name)
        Taps.tapCentreOf(itemViewOf(name))
    }

    fun tapCheckbox(name: String): ScanLocationsRobot {
        awaitRow(name)
        val checkedBefore = checkedStateOf(name)
        val itemView = itemViewOf(name)
        val checkbox = TestEnvironment.onMainThread {
            itemView.findViewById<View>(R.id.locationCheckBox)
                ?: throw ConditionTimeoutException("The row \"$name\" carries no checkbox")
        }
        Taps.tapCentreOf(checkbox)
        val checkedAfter = if (checkedBefore == CHECKED) UNCHECKED else CHECKED
        awaitCheckedState(name, checkedAfter)
        return this
    }

    fun isOnVolumeList(): Boolean = rows().isNotEmpty() && crumbs().isEmpty()

    fun enterVolume(volume: File): ScanLocationsRobot {
        awaitRowsSettled()
        if (isOnVolumeList()) {
            val target = rows().firstOrNull { it.path == volume.absolutePath }
                ?: throw ConditionTimeoutException(
                    "The mounted volume list has no row for ${volume.absolutePath}; crumbs=${crumbs()}, rows=${rows()}"
                )
            val targetDirectory = File(target.path)
            tapRow(target.name)
            awaitDirectoryRows(targetDirectory)
        } else {
            awaitDirectoryRows(volume)
        }
        return this
    }

    fun navigateInto(vararg names: String): ScanLocationsRobot {
        names.forEach { name ->
            val target = File(awaitRow(name).path)
            tapRow(name)
            awaitDirectoryRows(target)
        }
        return this
    }

    fun navigateIntoPathOf(directory: File, from: File): ScanLocationsRobot {
        enterVolume(from)
        val relative = directory.absolutePath
            .removePrefix(from.absolutePath)
            .trim(File.separatorChar)
        if (relative.isEmpty()) return this
        return navigateInto(*relative.split(File.separatorChar).toTypedArray())
    }

    fun goUp(): ScanLocationsRobot {
        if (isOnVolumeList()) {
            pressBack()
            awaitFinished()
            return this
        }

        val currentRows = rows()
        val parentPaths = currentRows.filterNot { it.isUp }
            .mapNotNull { File(it.path).parentFile?.absolutePath }
            .toSet()
        if (parentPaths.size != 1) {
            throw ConditionTimeoutException(
                "Cannot derive the current scan directory from one common row parent; " +
                    "parentPaths=$parentPaths, crumbs=${crumbs()}, rows=$currentRows"
            )
        }
        val currentDirectory = File(parentPaths.single())
        val volumes = volumeRoots()
        val atVolumeRoot = volumes.any { it.absolutePath == currentDirectory.absolutePath }
        val parentDirectory = currentDirectory.parentFile
        if (!atVolumeRoot && parentDirectory == null) {
            throw ConditionTimeoutException(
                "The current scan directory ${currentDirectory.absolutePath} has no parent; " +
                    "crumbs=${crumbs()}, rows=$currentRows"
            )
        }

        pressBack()
        when {
            atVolumeRoot && volumes.size == 1 -> awaitFinished()
            atVolumeRoot -> awaitVolumeRows()
            else -> awaitDirectoryRows(parentDirectory!!)
        }
        return this
    }

    fun isSaveEnabled(): Boolean = TestEnvironment.onMainThread {
        saveButton().isEnabled
    }

    fun awaitSaveEnabled(enabled: Boolean, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            "the Save button reports enabled=$enabled",
            timeoutMs,
            read = { isSaveEnabled() },
        ) { it == enabled }
    }

    fun tapSave(): ScanLocationsRobot {
        val button = TestEnvironment.onMainThread { saveButton() }
        Taps.tapCentreOf(button)
        return this
    }

    fun tapSaveExpectingNoEffect(): ScanLocationsRobot {
        val button = TestEnvironment.onMainThread { saveButton() }
        Taps.tapCentreOf(button)
        if (!isShowing()) {
            throw AssertionError(
                "Tapping the disabled Save button closed the scan locations picker, so the empty scope was saved"
            )
        }
        if (isSaveEnabled()) {
            throw AssertionError("The scan locations picker stayed open but its disabled Save button became enabled")
        }
        return this
    }

    fun leaveWithBack(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): ScanLocationsRobot {
        repeat(MAX_BACK_PRESSES) {
            if (!isShowing()) {
                awaitFinished(timeoutMs)
                return this
            }
            goUp()
        }
        awaitFinished(timeoutMs)
        return this
    }

    private fun awaitRowsSettled(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the scan locations picker commits a nonempty row list", timeoutMs) {
            rows().isNotEmpty()
        }
    }

    private fun awaitDirectoryRows(
        directory: File,
        timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS,
    ): List<ScanLocationRow> {
        val volumePaths = volumeRoots().map { it.absolutePath }.toSet()
        val atVolumeRoot = directory.absolutePath in volumePaths
        val expectedPaths = directory.listFiles()
            .orEmpty()
            .filter { child ->
                child.isDirectory &&
                    !child.isHidden &&
                    !(atVolumeRoot && (child.name == "Android" || child.name == "data"))
            }
            .sortedBy { it.name.lowercase() }
            .map { it.absolutePath }
        if (expectedPaths.isEmpty()) {
            throw ConditionTimeoutException(
                "The semantic directory gate cannot observe an empty destination at ${directory.absolutePath}. " +
                    "Expected a fixture with at least one visible child directory; crumbs=${crumbs()}, rows=${rows()}"
            )
        }

        return try {
            Await.untilValue(
                condition = "the picker commits exactly the visible children of ${directory.absolutePath}",
                timeoutMs = timeoutMs,
                read = { rows() to crumbs() },
            ) { (currentRows, currentCrumbs) ->
                currentRows.filterNot { it.isUp }.map { it.path } == expectedPaths &&
                    if (atVolumeRoot) {
                        currentCrumbs.isNotEmpty()
                    } else {
                        currentCrumbs.lastOrNull() == directory.name
                    }
            }.first
        } catch (error: ConditionTimeoutException) {
            throw ConditionTimeoutException(
                "Timed out waiting for directory ${directory.absolutePath}; expectedPaths=$expectedPaths, " +
                    "crumbs=${crumbs()}, rows=${rows()}. ${error.message}"
            )
        }
    }

    private fun awaitVolumeRows(
        timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS,
    ): List<ScanLocationRow> {
        val expectedPaths = volumeRoots().map { it.absolutePath }.toSet()
        return try {
            Await.untilValue(
                condition = "the picker commits exactly the mounted volume roots $expectedPaths",
                timeoutMs = timeoutMs,
                read = { rows() to crumbs() },
            ) { (currentRows, currentCrumbs) ->
                val actualPaths = currentRows.map { it.path }
                currentCrumbs.isEmpty() &&
                    currentRows.none { it.isUp } &&
                    actualPaths.size == expectedPaths.size &&
                    actualPaths.toSet() == expectedPaths
            }.first
        } catch (error: ConditionTimeoutException) {
            throw ConditionTimeoutException(
                "Timed out waiting for mounted volumes $expectedPaths; crumbs=${crumbs()}, rows=${rows()}. " +
                    error.message
            )
        }
    }

    private fun volumeRoots(): List<File> =
        StorageManager().volumeRoots(TestEnvironment.targetContext)

    private fun itemViewOf(name: String): View {
        val recycler = requireRecycler()
        val position = TestEnvironment.onMainThread {
            val adapter = requireLocationsAdapter()
            val index = adapter.currentList.indexOfFirst { it.name == name }
            if (index < 0) {
                throw AssertionError("No scan locations row is named \"$name\". Rows: ${rowNames()}")
            }
            offsetOf(recycler, adapter) + index
        }
        TestEnvironment.onMainThread { recycler.scrollToPosition(position) }
        TestEnvironment.waitForIdleSync()
        return Await.untilNotNull("the scan locations row \"$name\" is bound and shown") {
            TestEnvironment.onMainThread {
                recycler.findViewHolderForAdapterPosition(position)?.itemView?.takeIf { it.isShown }
            }
        }
    }

    private fun crumbViews(): List<TextView> {
        val recycler = recyclerOrNull() ?: return emptyList()
        val adapter = subAdapters(recycler).filterIsInstance<BreadcrumbAdapter>().firstOrNull()
            ?: return emptyList()
        if (adapter.itemCount == 0) return emptyList()
        val holder = recycler.findViewHolderForAdapterPosition(offsetOf(recycler, adapter))
            ?: return emptyList()
        val container = holder.itemView.findViewById<ViewGroup>(R.id.breadcrumbContainer)
            ?: return emptyList()
        return (0 until container.childCount).mapNotNull { container.getChildAt(it) as? TextView }
    }

    private fun activity(): ScanLocationsActivity =
        TestEnvironment.resumedActivityOfType(ScanLocationsActivity::class.java) as? ScanLocationsActivity
            ?: throw ConditionTimeoutException("No ScanLocationsActivity is resumed")

    private fun saveButton(): View = activity().findViewById(R.id.saveLocationsButton)
        ?: throw ConditionTimeoutException("ScanLocationsActivity holds no saveLocationsButton")

    private fun recyclerOrNull(): RecyclerView? = TestEnvironment.onMainThread {
        (TestEnvironment.resumedActivityOfType(ScanLocationsActivity::class.java))
            ?.findViewById(R.id.scanLocationsRecyclerView)
    }

    private fun requireRecycler(): RecyclerView = recyclerOrNull()
        ?: throw ConditionTimeoutException("ScanLocationsActivity holds no scanLocationsRecyclerView")

    private fun locationsAdapter(): ScanLocationsAdapter? {
        val recycler = recyclerOrNull() ?: return null
        return subAdapters(recycler).filterIsInstance<ScanLocationsAdapter>().firstOrNull()
    }

    private fun requireLocationsAdapter(): ScanLocationsAdapter = locationsAdapter()
        ?: throw ConditionTimeoutException("The scan locations list holds no ScanLocationsAdapter")

    private fun subAdapters(recycler: RecyclerView): List<RecyclerView.Adapter<*>> {
        val adapter = recycler.adapter ?: return emptyList()
        return if (adapter is ConcatAdapter) adapter.adapters else listOf(adapter)
    }

    private fun offsetOf(recycler: RecyclerView, target: RecyclerView.Adapter<*>): Int {
        var offset = 0
        for (sub in subAdapters(recycler)) {
            if (sub === target) return offset
            offset += sub.itemCount
        }
        throw ConditionTimeoutException(
            "${target::class.java.simpleName} is not part of the scan locations ConcatAdapter"
        )
    }

    companion object {

        const val MAX_BACK_PRESSES = 10

        val CHECKED = MaterialCheckBox.STATE_CHECKED
        val UNCHECKED = MaterialCheckBox.STATE_UNCHECKED
        val INDETERMINATE = MaterialCheckBox.STATE_INDETERMINATE

        fun stateName(state: Int): String = when (state) {
            MaterialCheckBox.STATE_CHECKED -> "STATE_CHECKED"
            MaterialCheckBox.STATE_UNCHECKED -> "STATE_UNCHECKED"
            MaterialCheckBox.STATE_INDETERMINATE -> "STATE_INDETERMINATE"
            else -> state.toString()
        }
    }
}
