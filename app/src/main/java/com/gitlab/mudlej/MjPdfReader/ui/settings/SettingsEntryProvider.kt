// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.settings

import androidx.annotation.StringRes
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.ui.reader.actions.ConfigurableAction

internal class SettingsEntryProvider(private val preferences: Preferences) {

    fun entries(): List<SettingEntry> {
        return listOf(
            homeEntries(),
            appearanceEntries(),
            readingEntries(),
            controlEntries(),
            textEntries(),
            highlightingEntries(),
            translationEntries(),
            privacyEntries(),
            backupEntries(),
            advancedEntries(),
        ).flatten()
    }

    private fun homeEntries(): List<SettingEntry> {
        return listOf(
            switchEntry(
                page = SettingsPage.HOME,
                titleRes = R.string.disable_home_library_title,
                key = Preferences.homeDisabledKey,
                defaultValue = Preferences.homeDisabledDefault,
                summaryRes = R.string.disable_home_library_summary,
                keywords = listOf("home", "library", "disable", "launch", "start", "picker"),
            ),
            switchEntry(
                page = SettingsPage.HOME,
                titleRes = R.string.home_show_pdf_title_title,
                key = Preferences.homeShowPdfTitleKey,
                defaultValue = Preferences.homeShowPdfTitleDefault,
                summaryRes = R.string.home_show_pdf_title_summary,
                keywords = listOf("home", "title", "metadata", "name"),
            ),
            SettingEntry(
                page = SettingsPage.HOME,
                titleRes = R.string.home_progress_style_title,
                keywords = listOf("home", "progress", "ring", "bar", "indicator", "cover", "percent"),
            ) { breadcrumb ->
                homeProgressStylePreference(breadcrumb)
            },
            SettingEntry(
                page = SettingsPage.HOME,
                titleRes = R.string.home_grid_title_lines_title,
                summaryRes = R.string.home_grid_title_lines_summary,
                keywords = listOf("home", "title", "name", "lines", "grid", "rows", "wrap", "truncate"),
            ) { breadcrumb ->
                homeGridTitleLinesPreference(breadcrumb)
            },
            SettingEntry(
                page = SettingsPage.HOME,
                titleRes = R.string.home_list_title_lines_title,
                summaryRes = R.string.home_list_title_lines_summary,
                keywords = listOf("home", "title", "name", "lines", "list", "rows", "wrap", "truncate"),
            ) { breadcrumb ->
                homeListTitleLinesPreference(breadcrumb)
            },
            SettingEntry(
                page = SettingsPage.HOME,
                titleRes = R.string.home_title_ellipsize_title,
                summaryRes = R.string.home_title_ellipsize_summary,
                keywords = listOf("home", "title", "name", "ellipsis", "ellipsize", "truncate", "dots", "start", "middle", "end"),
            ) { breadcrumb ->
                homeTitleEllipsizePreference(breadcrumb)
            },
            SettingEntry(
                page = SettingsPage.HOME,
                titleRes = R.string.home_list_badges_title,
                summaryRes = R.string.home_list_badges_summary,
                keywords = listOf("home", "badge", "list", "pages", "progress", "size", "status", "date", "meta", "details"),
            ) { breadcrumb ->
                listCardBadgesPreference(breadcrumb)
            },
        )
    }

    private fun appearanceEntries(): List<SettingEntry> {
        return listOf(
            appThemeEntry(),
            SettingEntry(
                page = SettingsPage.APPEARANCE,
                titleRes = R.string.dark_theme_for_pdf,
                keywords = listOf("pdf", "theme", "dark", "night", "system"),
                sectionRes = R.string.appearance_section_theme,
            ) { breadcrumb ->
                pdfPagesThemePreference(breadcrumb = breadcrumb)
            },

            switchEntry(
                page = SettingsPage.APPEARANCE,
                titleRes = R.string.quality,
                key = Preferences.highQualityKey,
                defaultValue = Preferences.highQualityDefault,
                summaryRes = R.string.quality_summary,
                keywords = listOf("rendering", "quality"),
                sectionRes = R.string.appearance_section_rendering,
            ),
            switchEntry(
                page = SettingsPage.APPEARANCE,
                titleRes = R.string.anti_aliasing,
                key = Preferences.antiAliasingKey,
                defaultValue = Preferences.antiAliasingDefault,
                summaryRes = R.string.anti_aliasing_summary,
                keywords = listOf("rendering", "smooth"),
                sectionRes = R.string.appearance_section_rendering,
            ),

            switchEntry(
                page = SettingsPage.APPEARANCE,
                titleRes = R.string.space_between_pages,
                key = Preferences.spaceBetweenPagesKey,
                defaultValue = Preferences.spaceBetweenPagesDefault,
                keywords = listOf("spacing", "page gap"),
                sectionRes = R.string.appearance_section_page,
            ),
            switchEntry(
                page = SettingsPage.APPEARANCE,
                titleRes = R.string.always_hide_margins,
                key = Preferences.alwaysHideMarginsKey,
                defaultValue = Preferences.alwaysHideMarginsDefault,
                summaryRes = R.string.always_hide_margins_summary,
                keywords = listOf("crop", "margin", "margins"),
                sectionRes = R.string.appearance_section_page,
            ),
            switchEntry(
                page = SettingsPage.APPEARANCE,
                titleRes = R.string.show_scroll_handle_page_count_title,
                key = Preferences.showScrollHandlePageCountKey,
                defaultValue = Preferences.showScrollHandlePageCountDefault,
                summaryRes = R.string.show_scroll_handle_page_count_summary,
                keywords = listOf("scroll", "handle", "page count"),
                sectionRes = R.string.appearance_section_page,
            ),
            switchEntry(
                page = SettingsPage.APPEARANCE,
                titleRes = R.string.show_app_bar_page_count_title,
                key = Preferences.showAppBarPageCountKey,
                defaultValue = Preferences.showAppBarPageCountDefault,
                summaryRes = R.string.show_app_bar_page_count_summary,
                keywords = listOf("app bar", "toolbar", "title", "page count"),
                sectionRes = R.string.appearance_section_page,
            ),
            scrollingInfoCardEntry(),
        )
    }

    private fun readingEntries(): List<SettingEntry> {
        return listOf(
            SettingEntry(
                page = SettingsPage.READING,
                titleRes = R.string.reading_mode_title,
                summaryRes = R.string.reading_mode_summary,
                keywords = listOf(
                    "mode", "layout", "continuous", "vertical", "dual", "two pages", "double",
                    "spread", "facing", "side by side", "horizontal", "swipe", "single", "book",
                ),
                sectionRes = R.string.reading_section_layout,
            ) { breadcrumb ->
                readingModePreference(breadcrumb)
            },
            SettingEntry(
                page = SettingsPage.READING,
                titleRes = R.string.tap_to_turn_title,
                summaryRes = R.string.tap_to_turn_summary,
                keywords = listOf("tap", "turn", "edge", "zones", "page", "flip"),
                sectionRes = R.string.reading_section_layout,
            ) { breadcrumb ->
                tapToTurnPreference(breadcrumb)
            },
            switchEntry(
                page = SettingsPage.READING,
                titleRes = R.string.dual_page_first_page_alone_title,
                key = Preferences.dualPageFirstPageAloneKey,
                defaultValue = Preferences.dualPageFirstPageAloneDefault,
                summaryRes = R.string.dual_page_first_page_alone_summary,
                keywords = listOf("cover", "first page", "spread", "book"),
                sectionRes = R.string.reading_section_layout,
            ),
            SettingEntry(
                page = SettingsPage.READING,
                titleRes = R.string.page_fit_title,
                summaryRes = R.string.page_fit_summary,
                keywords = listOf("fit", "width", "height", "whole page", "scale", "zoom", "tablet", "landscape"),
                sectionRes = R.string.reading_section_layout,
            ) { breadcrumb ->
                pageFitPreference(breadcrumb)
            },

            switchEntry(
                page = SettingsPage.READING,
                titleRes = R.string.browser_scroll_mode_title,
                key = Preferences.browserScrollModeKey,
                defaultValue = Preferences.browserScrollModeDefault,
                summaryRes = R.string.browser_scroll_mode_summary,
                keywords = listOf("browser", "scroll", "pan", "lock", "diagonal"),
                sectionRes = R.string.reading_section_scrolling,
                refreshOnChange = true,
            ),
            switchEntry(
                page = SettingsPage.READING,
                titleRes = R.string.snap,
                key = Preferences.pageSnapKey,
                defaultValue = Preferences.pageSnapDefault,
                summaryRes = R.string.snap_summary,
                keywords = listOf("page", "scroll"),
                sectionRes = R.string.reading_section_scrolling,
            ),
            switchEntry(
                page = SettingsPage.READING,
                titleRes = R.string.fling,
                key = Preferences.pageFlingKey,
                defaultValue = Preferences.pageFlingDefault,
                summaryRes = R.string.fling_summary,
                keywords = listOf("page", "scroll", "swipe"),
                sectionRes = R.string.reading_section_scrolling,
            ),

            switchEntry(
                page = SettingsPage.READING,
                titleRes = R.string.drag_after_long_press_title,
                key = Preferences.longPressDragEnabledKey,
                defaultValue = Preferences.longPressDragEnabledDefault,
                summaryRes = R.string.drag_after_long_press_summary,
                keywords = listOf("long press", "hold", "drag", "scroll", "pan", "stuck", "move"),
                sectionRes = R.string.reading_section_scrolling,
            ),

            switchEntry(
                page = SettingsPage.READING,
                titleRes = R.string.disable_double_tap_zoom_title,
                key = Preferences.doubleTapZoomDisabledKey,
                defaultValue = Preferences.doubleTapZoomDisabledDefault,
                summaryRes = R.string.disable_double_tap_zoom_summary,
                keywords = listOf("double tap", "zoom", "gesture", "disable", "magnify"),
                sectionRes = R.string.reading_section_zoom,
            ),
            switchEntry(
                page = SettingsPage.READING,
                titleRes = R.string.disable_double_tap_drag_zoom_title,
                key = Preferences.doubleTapDragZoomDisabledKey,
                defaultValue = Preferences.doubleTapDragZoomDisabledDefault,
                summaryRes = R.string.disable_double_tap_drag_zoom_summary,
                keywords = listOf("double tap", "hold", "drag", "slide", "zoom", "gesture", "disable"),
                sectionRes = R.string.reading_section_zoom,
            ),
            switchEntry(
                page = SettingsPage.READING,
                titleRes = R.string.keep_screen_on,
                key = Preferences.screenOnKey,
                defaultValue = Preferences.screenOnDefault,
                summaryRes = R.string.keep_screen_on_summary,
                keywords = listOf("display", "sleep", "screen"),
                sectionRes = R.string.reading_section_session,
            ),
            switchEntry(
                page = SettingsPage.READING,
                titleRes = R.string.always_open_first_page_title,
                key = Preferences.alwaysOpenAtFirstPageKey,
                defaultValue = Preferences.alwaysOpenAtFirstPageDefault,
                summaryRes = R.string.always_open_first_page_summary,
                keywords = listOf("first page", "page 1", "start", "resume", "position"),
                sectionRes = R.string.reading_section_session,
            ),
            switchEntry(
                page = SettingsPage.READING,
                titleRes = R.string.always_horizontal,
                key = Preferences.alwaysHorizontalKey,
                defaultValue = Preferences.alwaysHorizontalDefault,
                summaryRes = R.string.always_horizontal_summary,
                keywords = listOf("landscape", "orientation", "rotate"),
                sectionRes = R.string.reading_section_session,
            ),
        )
    }

    private fun controlEntries(): List<SettingEntry> {
        return listOf(
            actionEntry(
                page = SettingsPage.CUSTOMIZE_CONTROLS,
                titleRes = R.string.primary_button_action,
                key = Preferences.primaryButtonActionKey,
                defaultValue = Preferences.primaryButtonActionDefault,
                currentValue = preferences::getPrimaryButtonAction,
                actions = ConfigurableAction.toolbarActions,
                keywords = listOf("toolbar", "app bar", "button", "action"),
                onActionSelected = preferences::setPrimaryButtonAction,
            ),
            actionEntry(
                page = SettingsPage.CUSTOMIZE_CONTROLS,
                titleRes = R.string.secondary_button_action,
                key = Preferences.secondaryButtonActionKey,
                defaultValue = Preferences.secondaryButtonActionDefault,
                currentValue = preferences::getSecondaryButtonAction,
                actions = ConfigurableAction.toolbarActions,
                keywords = listOf("toolbar", "app bar", "button", "action"),
                onActionSelected = preferences::setSecondaryButtonAction,
            ),
            shortcutBarEntry(),
            fullScreenButtonsEntry(),
            switchEntry(
                page = SettingsPage.CUSTOMIZE_CONTROLS,
                titleRes = R.string.turn_page_by_volume_buttons_title,
                key = Preferences.turnPageByVolumeButtonsKey,
                defaultValue = Preferences.turnPageByVolumeButtonsDefault,
                summaryRes = R.string.turn_page_by_volume_buttons_summary,
                keywords = listOf("volume", "buttons", "page turn"),
            ),
            switchEntry(
                page = SettingsPage.CUSTOMIZE_CONTROLS,
                titleRes = R.string.turn_page_by_mouse_buttons_title,
                key = Preferences.turnPageByMouseButtonsKey,
                defaultValue = Preferences.turnPageByMouseButtonsDefault,
                summaryRes = R.string.turn_page_by_mouse_buttons_summary,
                keywords = listOf("mouse", "buttons", "page turn", "back", "forward"),
            ),
            switchEntry(
                page = SettingsPage.CUSTOMIZE_CONTROLS,
                titleRes = R.string.auto_full_screen,
                key = Preferences.autoFullScreenKey,
                defaultValue = Preferences.autoFullScreenDefault,
                summaryRes = R.string.auto_full_screen_summary,
                keywords = listOf("fullscreen", "startup", "open"),
            ),
            SettingEntry(
                page = SettingsPage.CUSTOMIZE_CONTROLS,
                titleRes = R.string.hide_delay_title,
                summaryRes = R.string.hide_delay_summary,
                keywords = listOf("hide", "delay", "overlay", "controls", "timeout", "fullscreen", "seconds"),
            ) { breadcrumb ->
                hideDelayPreference(breadcrumb)
            },
        )
    }

    private fun textEntries(): List<SettingEntry> {
        return listOf(
            switchEntry(
                page = SettingsPage.TEXT,
                titleRes = R.string.default_text_mode,
                key = Preferences.defaultTextModeKey,
                defaultValue = Preferences.defaultTextModeDefault,
                summaryRes = R.string.default_text_mode_summary,
                keywords = listOf("text", "extract", "mode"),
            ),
            switchEntry(
                page = SettingsPage.TEXT,
                titleRes = R.string.inline_text_selection_title,
                key = Preferences.inlineTextSelectionKey,
                defaultValue = Preferences.inlineTextSelectionDefault,
                summaryRes = R.string.inline_text_selection_summary,
                keywords = listOf("inline", "selection", "copy", "text"),
            ),
            switchEntry(
                page = SettingsPage.TEXT,
                titleRes = R.string.search_ignore_accents_title,
                key = Preferences.searchIgnoreAccentsKey,
                defaultValue = Preferences.searchIgnoreAccentsDefault,
                summaryRes = R.string.search_ignore_accents_summary,
                keywords = listOf("search", "accents", "diacritics"),
            ),
            switchEntry(
                page = SettingsPage.TEXT,
                titleRes = R.string.search_zoom_to_result_title,
                key = Preferences.searchZoomToResultKey,
                defaultValue = Preferences.searchZoomToResultDefault,
                summaryRes = R.string.search_zoom_to_result_summary,
                keywords = listOf("search", "zoom", "result", "navigate"),
            ),
        )
    }

    private fun highlightingEntries(): List<SettingEntry> {
        return listOf(
            SettingEntry(
                page = SettingsPage.HIGHLIGHTING,
                titleRes = R.string.highlight_colors,
                summaryRes = R.string.highlight_colors_summary,
                keywords = listOf("highlight", "color", "colors", "palette", "strip"),
            ) { breadcrumb ->
                highlightColorsPreference(breadcrumb = breadcrumb)
            },
            switchEntry(
                page = SettingsPage.HIGHLIGHTING,
                titleRes = R.string.detect_existing_highlights_title,
                key = Preferences.detectExistingHighlightsKey,
                defaultValue = Preferences.detectExistingHighlightsDefault,
                summaryRes = R.string.detect_existing_highlights_summary,
                keywords = listOf("highlight", "annotation", "selection", "detect"),
            ),
        )
    }

    private fun translationEntries(): List<SettingEntry> {
        return listOf(
            SettingEntry(
                page = SettingsPage.TRANSLATION,
                titleRes = R.string.translate_with_title,
                summaryRes = R.string.translate_with_summary,
                keywords = listOf("translate", "translation", "dictionary", "language", "apps", "web"),
                sectionRes = R.string.translation_section_translation,
            ) { breadcrumb ->
                translationModePreference(breadcrumb)
            },
            SettingEntry(
                page = SettingsPage.TRANSLATION,
                titleRes = R.string.translation_engine_title,
                keywords = listOf(
                    "translate", "translation", "engine", "google", "deepl", "bing",
                    "lingva", "libretranslate", "custom", "url",
                ),
                sectionRes = R.string.translation_section_translation,
            ) { breadcrumb ->
                translationEnginePreference(breadcrumb)
            },
            SettingEntry(
                page = SettingsPage.TRANSLATION,
                titleRes = R.string.translation_target_language_title,
                keywords = listOf("translate", "translation", "language", "target"),
                sectionRes = R.string.translation_section_translation,
            ) { breadcrumb ->
                translationTargetLanguagePreference(breadcrumb)
            },

            SettingEntry(
                page = SettingsPage.TRANSLATION,
                titleRes = R.string.dictionary_title,
                keywords = listOf("dictionary", "define", "definition", "offline", "wordnet", "download", "english"),
                sectionRes = R.string.translation_section_dictionary,
            ) { breadcrumb ->
                dictionaryPreference(breadcrumb)
            },
            SettingEntry(
                page = SettingsPage.TRANSLATION,
                titleRes = R.string.dictionary_define_words_title,
                summaryRes = R.string.dictionary_define_words_summary,
                keywords = listOf("dictionary", "define", "definition", "word", "lookup"),
                sectionRes = R.string.translation_section_dictionary,
            ) { breadcrumb ->
                dictionaryDefineWordsPreference(breadcrumb)
            },
        )
    }

    private fun privacyEntries(): List<SettingEntry> {
        return listOf(
            switchEntry(
                page = SettingsPage.HISTORY_PRIVACY,
                titleRes = R.string.history_enabled_title,
                key = Preferences.historyEnabledKey,
                defaultValue = Preferences.historyEnabledDefault,
                summaryRes = R.string.history_enabled_summary,
                keywords = listOf("history", "privacy", "save", "recent", "remember", "positions"),
                sectionRes = R.string.privacy_section_privacy,
            ),
            SettingEntry(
                page = SettingsPage.HISTORY_PRIVACY,
                titleRes = R.string.keep_shared_copies_title,
                summaryRes = R.string.keep_shared_copies_summary,
                keywords = listOf("copy", "share", "import", "temporary", "documents", "whatsapp", "telegram", "ask"),
                sectionRes = R.string.privacy_section_privacy,
            ) { breadcrumb ->
                sharedCopyModePreference(breadcrumb)
            },
            SettingEntry(
                page = SettingsPage.HISTORY_PRIVACY,
                titleRes = R.string.reading_history,
                summaryRes = R.string.reading_history_row_summary,
                keywords = listOf("history", "recent", "view", "records", "privacy"),
                sectionRes = R.string.privacy_section_privacy,
            ) { breadcrumb ->
                readingHistoryPreference(breadcrumb)
            },

            SettingEntry(
                page = SettingsPage.HISTORY_PRIVACY,
                titleRes = R.string.clear_reading_history_title,
                summaryRes = R.string.clear_reading_history_summary,
                keywords = listOf("clear", "delete", "history", "recent", "positions", "privacy"),
                sectionRes = R.string.privacy_section_clear,
            ) { breadcrumb ->
                clearReadingHistoryPreference(breadcrumb)
            },
            SettingEntry(
                page = SettingsPage.HISTORY_PRIVACY,
                titleRes = R.string.clear_saved_passwords_title,
                summaryRes = R.string.clear_saved_passwords_summary,
                keywords = listOf("clear", "delete", "passwords", "privacy"),
                sectionRes = R.string.privacy_section_clear,
            ) { breadcrumb ->
                clearSavedPasswordsPreference(breadcrumb)
            },
            SettingEntry(
                page = SettingsPage.HISTORY_PRIVACY,
                titleRes = R.string.clear_bookmarks_title,
                summaryRes = R.string.clear_bookmarks_summary,
                keywords = listOf("clear", "delete", "bookmarks", "privacy"),
                sectionRes = R.string.privacy_section_clear,
            ) { breadcrumb ->
                clearBookmarksPreference(breadcrumb)
            },
            SettingEntry(
                page = SettingsPage.HISTORY_PRIVACY,
                titleRes = R.string.clear_annotation_journals_title,
                summaryRes = R.string.clear_annotation_journals_summary,
                keywords = listOf("clear", "delete", "highlights", "signature", "recovery", "privacy"),
                sectionRes = R.string.privacy_section_clear,
            ) { breadcrumb ->
                clearAnnotationJournalsPreference(breadcrumb)
            },
        )
    }

    private fun backupEntries(): List<SettingEntry> {
        return listOf(
            SettingEntry(
                page = SettingsPage.BACKUP,
                titleRes = R.string.backup_folder_title,
                summaryRes = R.string.backup_folder_summary_unset,
                keywords = listOf("backup", "folder", "location", "directory", "save"),
            ) { breadcrumb ->
                backupFolderPreference(breadcrumb)
            },
            SettingEntry(
                page = SettingsPage.BACKUP,
                titleRes = R.string.auto_backup_title,
                summaryRes = R.string.auto_backup_summary,
                keywords = listOf("backup", "automatic", "daily", "schedule", "auto"),
            ) { breadcrumb ->
                autoBackupSwitchPreference(breadcrumb)
            },
            SettingEntry(
                page = SettingsPage.BACKUP,
                titleRes = R.string.auto_backup_time_title,
                summaryRes = R.string.auto_backup_time_summary,
                keywords = listOf("backup", "time", "schedule", "daily", "hour"),
            ) { breadcrumb ->
                autoBackupTimePreference(breadcrumb)
            },
            SettingEntry(
                page = SettingsPage.BACKUP,
                titleRes = R.string.backup_export_title,
                summaryRes = R.string.backup_export_summary,
                keywords = listOf("backup", "export", "save", "data", "transfer", "progress"),
            ) { breadcrumb ->
                backupExportPreference(breadcrumb)
            },
            SettingEntry(
                page = SettingsPage.BACKUP,
                titleRes = R.string.backup_import_title,
                summaryRes = R.string.backup_import_summary,
                keywords = listOf("backup", "import", "restore", "data", "transfer", "progress"),
            ) { breadcrumb ->
                backupImportPreference(breadcrumb)
            },
            SettingEntry(
                page = SettingsPage.BACKUP,
                titleRes = R.string.backup_restore_snapshot_title,
                keywords = listOf("backup", "restore", "snapshot", "import", "undo", "recover"),
            ) { breadcrumb ->
                backupRestoreSnapshotPreference(breadcrumb)
            },
        )
    }

    private fun advancedEntries(): List<SettingEntry> {
        return listOf(
            switchEntry(
                page = SettingsPage.ADVANCED,
                titleRes = R.string.double_tap_to_exit,
                key = Preferences.doubleTapToExitEnabledKey,
                defaultValue = Preferences.doubleTapToExitEnabledDefault,
                summaryRes = R.string.double_tap_to_exit_summary,
                keywords = listOf("exit", "back"),
            ),
            switchEntry(
                page = SettingsPage.ADVANCED,
                titleRes = R.string.three_step_double_tap_zoom,
                key = Preferences.doubleTapThreeStepZoomKey,
                defaultValue = Preferences.doubleTapThreeStepZoomDefault,
                summaryRes = R.string.three_step_double_tap_zoom_summary,
                keywords = listOf("double tap", "zoom", "steps", "magnify"),
            ),
            switchEntry(
                page = SettingsPage.ADVANCED,
                titleRes = R.string.separate_windows_title,
                key = Preferences.openPdfsInSeparateWindowsKey,
                defaultValue = Preferences.openPdfsInSeparateWindowsDefault,
                summaryRes = R.string.separate_windows_summary,
                keywords = listOf("window", "instance", "recent", "task", "separate"),
            ),
            floatPreferenceEntry(
                page = SettingsPage.ADVANCED,
                titleRes = R.string.part_size,
                summaryRes = R.string.part_size_summary,
                key = Preferences.partSizeKey,
                currentValue = preferences::getPartSize,
                defaultValue = Preferences.partSizeDefault,
                minValue = Preferences.minPartSize,
                maxValue = Preferences.maxPartSize,
                keywords = listOf("advanced", "render", "cache", "part"),
                onValueSelected = preferences::setPartSize,
            ),
            floatPreferenceEntry(
                page = SettingsPage.ADVANCED,
                titleRes = R.string.max_zoom,
                summaryRes = R.string.max_zoom_summary,
                key = Preferences.maxZoomKey,
                currentValue = preferences::getMaxZoom,
                defaultValue = Preferences.maxZoomDefault,
                minValue = Preferences.minMaxZoom,
                maxValue = Preferences.maxMaxZoom,
                keywords = listOf("advanced", "zoom", "scale"),
                onValueSelected = preferences::setMaxZoom,
            ),
        )
    }

    private fun appThemeEntry(): SettingEntry {
        return SettingEntry(
            page = SettingsPage.APPEARANCE,
            titleRes = R.string.dark_theme_for_app,
            keywords = listOf("ui", "theme", "dark", "night", "system"),
            sectionRes = R.string.appearance_section_theme,
        ) { breadcrumb ->
            interfaceThemePreference(breadcrumb = breadcrumb)
        }
    }

    private fun shortcutBarEntry(): SettingEntry {
        return SettingEntry(
            page = SettingsPage.CUSTOMIZE_CONTROLS,
            titleRes = R.string.shortcut_bar_buttons,
            summaryRes = R.string.shortcut_bar_buttons_summary,
            keywords = listOf("shortcut", "buttons", "actions"),
        ) { breadcrumb ->
            shortcutBarButtonsPreference(breadcrumb = breadcrumb)
        }
    }

    private fun fullScreenButtonsEntry(): SettingEntry {
        return SettingEntry(
            page = SettingsPage.CUSTOMIZE_CONTROLS,
            titleRes = R.string.fullscreen_buttons,
            summaryRes = R.string.fullscreen_buttons_summary,
            keywords = listOf("fullscreen", "buttons", "actions"),
        ) { breadcrumb ->
            fullScreenButtonsPreference(breadcrumb = breadcrumb)
        }
    }

    private fun scrollingInfoCardEntry(): SettingEntry {
        return SettingEntry(
            page = SettingsPage.APPEARANCE,
            titleRes = R.string.scrolling_info_card,
            summaryRes = R.string.scrolling_info_card_summary,
            keywords = listOf("scrolling", "fullscreen", "info", "card", "time", "clock", "page", "percent", "progress", "title", "file"),
            sectionRes = R.string.appearance_section_page,
        ) { breadcrumb ->
            scrollingInfoCardPreference(breadcrumb = breadcrumb)
        }
    }
}

private fun switchEntry(
    page: SettingsPage,
    @StringRes titleRes: Int,
    key: String,
    defaultValue: Boolean,
    @StringRes summaryRes: Int? = null,
    keywords: List<String> = emptyList(),
    @StringRes sectionRes: Int? = null,
    refreshOnChange: Boolean = false,
): SettingEntry {
    return SettingEntry(
        page = page,
        titleRes = titleRes,
        summaryRes = summaryRes,
        keywords = keywords,
        sectionRes = sectionRes,
    ) { breadcrumb ->
        if (refreshOnChange) {
            refreshingSwitchPreference(
                titleRes = titleRes,
                key = key,
                defaultValue = defaultValue,
                summaryRes = summaryRes,
                breadcrumb = breadcrumb,
            )
        } else {
            switchPreference(
                titleRes = titleRes,
                key = key,
                defaultValue = defaultValue,
                summaryRes = summaryRes,
                breadcrumb = breadcrumb,
            )
        }
    }
}

private fun actionEntry(
    page: SettingsPage,
    @StringRes titleRes: Int,
    key: String,
    defaultValue: String,
    currentValue: () -> String,
    actions: List<ConfigurableAction>,
    keywords: List<String>,
    onActionSelected: (String) -> Unit,
): SettingEntry {
    return SettingEntry(
        page = page,
        titleRes = titleRes,
        keywords = keywords,
    ) { breadcrumb ->
        actionPreference(
            titleRes = titleRes,
            key = key,
            defaultValue = defaultValue,
            currentValue = currentValue(),
            actions = actions,
            breadcrumb = breadcrumb,
            onActionSelected = onActionSelected,
        )
    }
}

private fun floatPreferenceEntry(
    page: SettingsPage,
    @StringRes titleRes: Int,
    @StringRes summaryRes: Int,
    key: String,
    currentValue: () -> Float,
    defaultValue: Float,
    minValue: Float,
    maxValue: Float,
    keywords: List<String>,
    onValueSelected: (Float) -> Unit,
): SettingEntry {
    return SettingEntry(
        page = page,
        titleRes = titleRes,
        summaryRes = summaryRes,
        keywords = keywords,
    ) { breadcrumb ->
        floatPreference(
            titleRes = titleRes,
            summaryRes = summaryRes,
            key = key,
            currentValue = currentValue(),
            defaultValue = defaultValue,
            minValue = minValue,
            maxValue = maxValue,
            breadcrumb = breadcrumb,
            onValueSelected = onValueSelected,
        )
    }
}
