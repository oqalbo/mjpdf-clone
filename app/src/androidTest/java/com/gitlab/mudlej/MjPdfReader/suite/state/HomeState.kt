// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.state

import android.view.View
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeActivity
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeTab

class HomeState internal constructor(val description: RootDescription) {

    val generation: Int? get() = description.int(HOME_GROUP, GENERATION)
    val renderedItems: Int? get() = description.int(HOME_GROUP, ITEMS)
    val searching: Boolean? get() = description.boolean(HOME_GROUP, SEARCH)
    val accessRevision: Long? get() = description.long(HOME_GROUP, ACCESS_REVISION)
    val storageAccess: Boolean? get() = description.boolean(HOME_GROUP, STORAGE_ACCESS)

    val tab: HomeTab?
        get() = description.value(HOME_GROUP, TAB)?.let { token ->
            HomeTab.entries.firstOrNull { it.name == token }
                ?: throw RootDescriptionFormatException(
                    "Group $HOME_GROUP key $TAB holds \"$token\" which is not a HomeTab, in: ${description.raw}"
                )
        }

    val scanLoaded: Boolean? get() = description.boolean(SCAN_GROUP, LOADED)
    val scanning: Boolean? get() = description.boolean(SCAN_GROUP, SCANNING)
    val scanGeneration: Long? get() = description.long(SCAN_GROUP, "generation")
    val scanPhase: String? get() = description.value(SCAN_GROUP, "phase")
    val backendRevision: Long? get() = description.long(SCAN_GROUP, "backendRevision")
    val uiRevision: Long? get() = description.long(SCAN_GROUP, "uiRevision")
    val showProgress: Boolean? get() = description.boolean(SCAN_GROUP, "showProgress")

    val layoutDirection: LayoutDirection? get() = description.layoutDirection

    val hasHomeGroup: Boolean get() = description.group(HOME_GROUP) != null

    override fun toString(): String = description.raw

    companion object {
        const val HOME_GROUP = "home"
        const val SCAN_GROUP = "scan"

        const val GENERATION = "gen"
        const val ITEMS = "items"
        const val TAB = "tab"
        const val SEARCH = "search"
        const val ACCESS_REVISION = "accessRevision"
        const val STORAGE_ACCESS = "storageAccess"

        const val LOADED = "loaded"
        const val SCANNING = "scanning"
    }
}

object HomeStateSource {

    fun readRaw(): String? = TestEnvironment.onMainThread {
        findHomeRoot()?.contentDescription?.toString()
    }

    fun read(): HomeState? =
        readRaw()?.takeIf { it.isNotEmpty() }?.let { HomeState(RootDescriptionParser.parse(it)) }

    private fun findHomeRoot(): View? {
        val activity = TestEnvironment.resumedActivityOfType(HomeActivity::class.java) ?: return null
        return activity.findViewById(R.id.homeRoot)
    }
}
