// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.actions

import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.doOnPreDraw
import com.gitlab.mudlej.MjPdfReader.BuildConfig
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity

private const val PREWARM_ICON_BATCH = 6

class ReaderMenu(
    private val activity: MainActivity,
    private val actionResolver: ConfigurableActionResolver,
    private val hasFile: () -> Boolean,
    private val toggleSecondBar: () -> Unit,
) {

    private var iconsPrewarmed = false

    fun show() {
        val startMs = if (BuildConfig.DEBUG) SystemClock.uptimeMillis() else 0L
        val dialog = showReaderActionsDialog(activity, menuContent())
        if (BuildConfig.DEBUG) {
            dialog.window!!.decorView.doOnPreDraw {
                Log.d("MjPdfPerf", "menu open: ${SystemClock.uptimeMillis() - startMs}ms")
            }
        }
    }

    fun prewarmIcons() {
        if (iconsPrewarmed) {
            return
        }
        iconsPrewarmed = true
        val icons = menuContent().sections.flatMap { it.actions }.map { it.iconRes }.distinct()
        if (icons.isEmpty()) {
            return
        }
        var index = 0
        Looper.getMainLooper().queue.addIdleHandler {
            val end = minOf(index + PREWARM_ICON_BATCH, icons.size)
            while (index < end) {
                AppCompatResources.getDrawable(activity, icons[index])
                index++
            }
            index < icons.size
        }
    }

    private fun menuContent(): ReaderMenuContent {
        return ReaderMenuContent(
            sections = listOf(
                ReaderMenuSection(actionsSection()),
                ReaderMenuSection(pagesSection()),
            ),
        )
    }

    private fun actionsSection(): List<ReaderAction> {
        return listOfNotNull(
            action(ConfigurableAction.OPEN_LOCAL),
            action(ConfigurableAction.SWITCH_THEME),
            action(ConfigurableAction.SEARCH),
            action(ConfigurableAction.GO_TO_PAGE),
            action(ConfigurableAction.BOOKMARK_PAGE),
            action(ConfigurableAction.FULLSCREEN),
            action(ConfigurableAction.READING_DIRECTION),
            action(ConfigurableAction.CROP_MARGINS),
            action(ConfigurableAction.SCREENSHOT),
            action(ConfigurableAction.EXTRACT_TEXT),
            action(ConfigurableAction.SHARE),
            action(ConfigurableAction.PRINT),
            action(ConfigurableAction.ADD_SIGNATURE),
            action(ConfigurableAction.RELOAD),
            action(ConfigurableAction.OPEN_ONLINE),
            action(ConfigurableAction.FILE_METADATA),
            action(ConfigurableAction.INCOGNITO),
            ReaderAction(R.string.toggle_shortcuts, R.drawable.ic_awesome, visible = hasFile()) {
                toggleSecondBar()
            },
            navAction(ConfigurableAction.NAV_BACK),
            navAction(ConfigurableAction.NAV_FORWARD),
            navAction(ConfigurableAction.NAV_HISTORY),
        )
    }

    private fun pagesSection(): List<ReaderAction> {
        return listOfNotNull(
            action(ConfigurableAction.TABLE_OF_CONTENTS),
            action(ConfigurableAction.USER_BOOKMARKS),
            action(ConfigurableAction.USER_NOTES),
            action(ConfigurableAction.USER_HIGHLIGHTS),
            action(ConfigurableAction.TEXT_MODE),
            action(ConfigurableAction.LINKS_IN_FILE),
            action(ConfigurableAction.SETTINGS),
            action(ConfigurableAction.ABOUT),
        )
    }

    private fun action(action: ConfigurableAction): ReaderAction? {
        val configuredAction = actionResolver.action(action) ?: return null
        return ReaderAction(
            configuredAction.titleRes,
            configuredAction.iconRes,
            visible = configuredAction.visible,
        ) {
            configuredAction.run()
        }
    }

    private fun navAction(action: ConfigurableAction): ReaderAction? {
        val configuredAction = actionResolver.action(action) ?: return null
        return ReaderAction(
            configuredAction.titleRes,
            configuredAction.iconRes,
            visible = hasFile(),
            enabled = configuredAction.visible,
        ) {
            configuredAction.run()
        }
    }
}
