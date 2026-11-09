// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.actions

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.gitlab.mudlej.MjPdfReader.R

data class ConfiguredAction(
    @StringRes val titleRes: Int,
    @DrawableRes val iconRes: Int,
    val visible: Boolean = true,
    val checked: Boolean? = null,
    val run: () -> Unit,
)

class ConfigurableActionResolver(
    private val hasFile: () -> Boolean,
    private val horizontalScrollEnabled: () -> Boolean,
    private val cropMarginsEnabled: () -> Boolean,
    private val dualPageEnabled: () -> Boolean,
    private val isPdfDarkTheme: () -> Boolean,
    private val canNavigateBack: () -> Boolean,
    private val canNavigateForward: () -> Boolean,
    private val canShowNavigationHistory: () -> Boolean,
    private val isCurrentPageBookmarked: () -> Boolean,
    private val isIncognito: () -> Boolean,
    private val handlers: Handlers,
) {

    data class Handlers(
        val toggleFullscreen: () -> Unit,
        val exitFullscreen: () -> Unit,
        val rotate: () -> Unit,
        val toggleHorizontalLock: () -> Unit,
        val readingDirection: () -> Unit,
        val toggleZoomLock: () -> Unit,
        val toggleCropMargins: () -> Unit,
        val toggleDualPage: () -> Unit,
        val screenshot: () -> Unit,
        val switchTheme: () -> Unit,
        val navigateBack: () -> Unit,
        val navigateForward: () -> Unit,
        val showNavigationHistory: () -> Unit,
        val reload: () -> Unit,
        val openLocal: () -> Unit,
        val openOnline: () -> Unit,
        val search: () -> Unit,
        val goToPage: () -> Unit,
        val extractText: () -> Unit,
        val textMode: () -> Unit,
        val share: () -> Unit,
        val settings: () -> Unit,
        val fileMetadata: () -> Unit,
        val about: () -> Unit,
        val tableOfContents: () -> Unit,
        val toggleBookmark: () -> Unit,
        val userBookmarks: () -> Unit,
        val userNotes: () -> Unit,
        val userHighlights: () -> Unit,
        val linksInFile: () -> Unit,
        val print: () -> Unit,
        val addSignature: () -> Unit,
        val toggleIncognito: () -> Unit,
    )

    fun action(actionId: String): ConfiguredAction? {
        return action(ConfigurableAction.fromId(actionId))
    }

    fun action(action: ConfigurableAction): ConfiguredAction? {
        val fileAvailable = hasFile()
        return when (action) {
            ConfigurableAction.NONE -> null
            ConfigurableAction.FULLSCREEN -> ConfiguredAction(
                R.string.full_screen,
                R.drawable.ic_fullscreen_grey,
                visible = fileAvailable,
                run = handlers.toggleFullscreen,
            )
            ConfigurableAction.EXIT_FULLSCREEN -> ConfiguredAction(
                R.string.exit,
                R.drawable.close_icon,
                visible = fileAvailable,
                run = handlers.exitFullscreen,
            )
            ConfigurableAction.ROTATE -> ConfiguredAction(
                R.string.rotate,
                R.drawable.ic_screen_rotate,
                visible = fileAvailable,
                run = handlers.rotate,
            )
            ConfigurableAction.HORIZONTAL_LOCK -> ConfiguredAction(
                R.string.horizontal_lock_action,
                R.drawable.ic_horizontal_swipe,
                visible = fileAvailable,
                run = handlers.toggleHorizontalLock,
            )
            ConfigurableAction.READING_DIRECTION -> ConfiguredAction(
                R.string.reading_direction,
                R.drawable.ic_horizontal_swipe,
                visible = fileAvailable && horizontalScrollEnabled(),
                run = handlers.readingDirection,
            )
            ConfigurableAction.ZOOM_LOCK -> ConfiguredAction(
                R.string.zoom_lock,
                R.drawable.ic_zoom_out,
                visible = fileAvailable,
                run = handlers.toggleZoomLock,
            )
            ConfigurableAction.CROP_MARGINS -> ConfiguredAction(
                if (cropMarginsEnabled()) R.string.show_margins else R.string.crop_margins,
                if (cropMarginsEnabled()) R.drawable.ic_show_margins else R.drawable.ic_crop_margins,
                visible = fileAvailable,
                checked = cropMarginsEnabled(),
                run = handlers.toggleCropMargins,
            )
            ConfigurableAction.DUAL_PAGE -> ConfiguredAction(
                if (dualPageEnabled()) R.string.dual_page_off_action else R.string.dual_page_mode_title,
                R.drawable.ic_dual_page,
                visible = fileAvailable && !horizontalScrollEnabled(),
                checked = dualPageEnabled(),
                run = handlers.toggleDualPage,
            )
            ConfigurableAction.SCREENSHOT -> ConfiguredAction(
                R.string.screenshot,
                R.drawable.ic_screenshot,
                visible = fileAvailable,
                run = handlers.screenshot,
            )
            ConfigurableAction.SWITCH_THEME -> ConfiguredAction(
                if (isPdfDarkTheme()) R.string.switch_to_light_mode else R.string.switch_to_dark_mode,
                if (isPdfDarkTheme()) R.drawable.ic_light_mode else R.drawable.ic_dark_mode,
                visible = fileAvailable,
                checked = isPdfDarkTheme(),
                run = handlers.switchTheme,
            )
            ConfigurableAction.NAV_BACK -> ConfiguredAction(
                R.string.nav_back,
                R.drawable.ic_nav_back,
                visible = fileAvailable && canNavigateBack(),
                run = handlers.navigateBack,
            )
            ConfigurableAction.NAV_FORWARD -> ConfiguredAction(
                R.string.nav_forward,
                R.drawable.ic_nav_forward,
                visible = fileAvailable && canNavigateForward(),
                run = handlers.navigateForward,
            )
            ConfigurableAction.NAV_HISTORY -> ConfiguredAction(
                R.string.navigation_history,
                R.drawable.ic_history,
                visible = fileAvailable && canShowNavigationHistory(),
                run = handlers.showNavigationHistory,
            )
            ConfigurableAction.RELOAD -> ConfiguredAction(
                R.string.reload_pdf,
                R.drawable.ic_refresh,
                visible = fileAvailable,
                run = handlers.reload,
            )
            ConfigurableAction.OPEN_LOCAL -> ConfiguredAction(
                R.string.open_another_pdf,
                R.drawable.ic_folder,
                run = handlers.openLocal,
            )
            ConfigurableAction.OPEN_ONLINE -> ConfiguredAction(
                R.string.open_online_pdf,
                R.drawable.ic_link,
                run = handlers.openOnline,
            )
            ConfigurableAction.SEARCH -> ConfiguredAction(
                R.string.search,
                R.drawable.search_icon,
                visible = fileAvailable,
                run = handlers.search,
            )
            ConfigurableAction.GO_TO_PAGE -> ConfiguredAction(
                R.string.go_to_page,
                R.drawable.ic_shortcut,
                visible = fileAvailable,
                run = handlers.goToPage,
            )
            ConfigurableAction.EXTRACT_TEXT -> ConfiguredAction(
                R.string.copy_page_text,
                R.drawable.ic_copy,
                visible = fileAvailable,
                run = handlers.extractText,
            )
            ConfigurableAction.TEXT_MODE -> ConfiguredAction(
                R.string.text_mode,
                R.drawable.ic_text,
                visible = fileAvailable,
                run = handlers.textMode,
            )
            ConfigurableAction.SHARE -> ConfiguredAction(
                R.string.share_file,
                R.drawable.ic_share,
                visible = fileAvailable,
                run = handlers.share,
            )
            ConfigurableAction.SETTINGS -> ConfiguredAction(
                R.string.settings,
                R.drawable.ic_settings,
                run = handlers.settings,
            )
            ConfigurableAction.FILE_METADATA -> ConfiguredAction(
                R.string.file_metadata,
                R.drawable.meta_info,
                visible = fileAvailable,
                run = handlers.fileMetadata,
            )
            ConfigurableAction.ABOUT -> ConfiguredAction(
                R.string.action_about,
                R.drawable.info_icon,
                run = handlers.about,
            )
            ConfigurableAction.TABLE_OF_CONTENTS -> ConfiguredAction(
                R.string.table_of_contents,
                R.drawable.ic_book_bookmark,
                visible = fileAvailable,
                run = handlers.tableOfContents,
            )
            ConfigurableAction.BOOKMARK_PAGE -> ConfiguredAction(
                if (isCurrentPageBookmarked()) R.string.remove_bookmark else R.string.add_bookmark,
                if (isCurrentPageBookmarked()) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark_outline,
                visible = fileAvailable,
                checked = isCurrentPageBookmarked(),
                run = handlers.toggleBookmark,
            )
            ConfigurableAction.USER_BOOKMARKS -> ConfiguredAction(
                R.string.bookmarks,
                R.drawable.ic_bookmarks,
                visible = fileAvailable,
                run = handlers.userBookmarks,
            )
            ConfigurableAction.USER_NOTES -> ConfiguredAction(
                R.string.user_notes_title,
                R.drawable.ic_comment,
                visible = fileAvailable,
                run = handlers.userNotes,
            )
            ConfigurableAction.USER_HIGHLIGHTS -> ConfiguredAction(
                R.string.user_highlights_title,
                R.drawable.ic_highlight,
                visible = fileAvailable,
                run = handlers.userHighlights,
            )
            ConfigurableAction.LINKS_IN_FILE -> ConfiguredAction(
                R.string.links_in_file,
                R.drawable.ic_links_in_file,
                visible = fileAvailable,
                run = handlers.linksInFile,
            )
            ConfigurableAction.PRINT -> ConfiguredAction(
                R.string.print_file,
                R.drawable.ic_print,
                visible = fileAvailable,
                run = handlers.print,
            )
            ConfigurableAction.ADD_SIGNATURE -> ConfiguredAction(
                R.string.add_signature,
                R.drawable.ic_signature,
                visible = fileAvailable,
                run = handlers.addSignature,
            )
            ConfigurableAction.INCOGNITO -> ConfiguredAction(
                if (isIncognito()) R.string.incognito_exit else R.string.incognito,
                R.drawable.ic_incognito,
                checked = isIncognito(),
                run = handlers.toggleIncognito,
            )
            ConfigurableAction.BRIGHTNESS,
            ConfigurableAction.AUTO_SCROLL,
            ConfigurableAction.TOGGLE_LABELS -> null
        }
    }

    fun perform(actionId: String): Boolean {
        val action = action(actionId) ?: return false
        if (!action.visible) {
            return false
        }
        action.run()
        return true
    }
}
