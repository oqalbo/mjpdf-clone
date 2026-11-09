// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.about

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.gitlab.mudlej.MjPdfReader.R

data class FeatureEntry(
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
)

enum class FeatureTopic(
    @DrawableRes val iconRes: Int,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val entries: List<FeatureEntry>,
) {
    HOME(
        R.drawable.ic_home,
        R.string.features_topic_home,
        R.string.features_topic_home_subtitle,
        listOf(
            FeatureEntry(R.string.features_home_tabs_title, R.string.features_home_tabs_body),
            FeatureEntry(R.string.features_home_continue_title, R.string.features_home_continue_body),
            FeatureEntry(R.string.features_home_statuses_title, R.string.features_home_statuses_body),
            FeatureEntry(R.string.features_home_selection_title, R.string.features_home_selection_body),
            FeatureEntry(R.string.features_home_favorites_title, R.string.features_home_favorites_body),
            FeatureEntry(R.string.features_home_views_title, R.string.features_home_views_body),
            FeatureEntry(R.string.features_home_options_title, R.string.features_home_options_body),
            FeatureEntry(R.string.features_home_folders_title, R.string.features_home_folders_body),
            FeatureEntry(R.string.features_home_search_title, R.string.features_home_search_body),
            FeatureEntry(R.string.features_home_scan_title, R.string.features_home_scan_body),
            FeatureEntry(R.string.features_home_hidden_title, R.string.features_home_hidden_body),
            FeatureEntry(R.string.features_home_stats_title, R.string.features_home_stats_body),
            FeatureEntry(R.string.features_home_disable_title, R.string.features_home_disable_body),
        ),
    ),
    READING(
        R.drawable.ic_book_bookmark,
        R.string.features_topic_reading,
        R.string.features_topic_reading_subtitle,
        listOf(
            FeatureEntry(R.string.features_reading_modes_title, R.string.features_reading_modes_body),
            FeatureEntry(R.string.features_reading_fit_title, R.string.features_reading_fit_body),
            FeatureEntry(R.string.features_reading_dual_page_title, R.string.features_reading_dual_page_body),
            FeatureEntry(R.string.features_reading_snap_title, R.string.features_reading_snap_body),
            FeatureEntry(R.string.features_reading_browser_title, R.string.features_reading_browser_body),
            FeatureEntry(R.string.features_reading_auto_scroll_title, R.string.features_reading_auto_scroll_body),
            FeatureEntry(R.string.features_reading_dark_title, R.string.features_reading_dark_body),
            FeatureEntry(R.string.features_reading_margins_title, R.string.features_reading_margins_body),
            FeatureEntry(R.string.features_reading_text_mode_title, R.string.features_reading_text_mode_body),
            FeatureEntry(R.string.features_reading_fullscreen_title, R.string.features_reading_fullscreen_body),
            FeatureEntry(R.string.features_reading_info_card_title, R.string.features_reading_info_card_body),
            FeatureEntry(R.string.features_reading_goto_title, R.string.features_reading_goto_body),
            FeatureEntry(R.string.features_reading_page_turn_title, R.string.features_reading_page_turn_body),
            FeatureEntry(R.string.features_reading_screen_on_title, R.string.features_reading_screen_on_body),
            FeatureEntry(R.string.features_reading_first_page_title, R.string.features_reading_first_page_body),
            FeatureEntry(R.string.features_reading_online_title, R.string.features_reading_online_body),
            FeatureEntry(R.string.features_reading_quality_title, R.string.features_reading_quality_body),
        ),
    ),
    ANNOTATION(
        R.drawable.ic_highlight,
        R.string.features_topic_annotation,
        R.string.features_topic_annotation_subtitle,
        listOf(
            FeatureEntry(R.string.features_annotation_select_title, R.string.features_annotation_select_body),
            FeatureEntry(R.string.features_annotation_highlight_title, R.string.features_annotation_highlight_body),
            FeatureEntry(R.string.features_annotation_notes_title, R.string.features_annotation_notes_body),
            FeatureEntry(R.string.features_annotation_edit_title, R.string.features_annotation_edit_body),
            FeatureEntry(R.string.features_annotation_lists_title, R.string.features_annotation_lists_body),
            FeatureEntry(R.string.features_annotation_detect_title, R.string.features_annotation_detect_body),
            FeatureEntry(R.string.features_annotation_signature_title, R.string.features_annotation_signature_body),
            FeatureEntry(R.string.features_annotation_forms_title, R.string.features_annotation_forms_body),
            FeatureEntry(R.string.features_annotation_saving_title, R.string.features_annotation_saving_body),
            FeatureEntry(R.string.features_annotation_recovery_title, R.string.features_annotation_recovery_body),
        ),
    ),
    NAVIGATION(
        R.drawable.ic_bookmarks,
        R.string.features_topic_navigation,
        R.string.features_topic_navigation_subtitle,
        listOf(
            FeatureEntry(R.string.features_navigation_toc_title, R.string.features_navigation_toc_body),
            FeatureEntry(R.string.features_navigation_bookmarks_title, R.string.features_navigation_bookmarks_body),
            FeatureEntry(R.string.features_navigation_history_title, R.string.features_navigation_history_body),
            FeatureEntry(R.string.features_navigation_links_title, R.string.features_navigation_links_body),
            FeatureEntry(R.string.features_navigation_search_title, R.string.features_navigation_search_body),
        ),
    ),
    TOOLS(
        R.drawable.ic_screenshot,
        R.string.features_topic_tools,
        R.string.features_topic_tools_subtitle,
        listOf(
            FeatureEntry(R.string.features_tools_screenshot_title, R.string.features_tools_screenshot_body),
            FeatureEntry(R.string.features_tools_print_title, R.string.features_tools_print_body),
            FeatureEntry(R.string.features_tools_share_title, R.string.features_tools_share_body),
            FeatureEntry(R.string.features_tools_extract_title, R.string.features_tools_extract_body),
            FeatureEntry(R.string.features_tools_metadata_title, R.string.features_tools_metadata_body),
            FeatureEntry(R.string.features_tools_reload_title, R.string.features_tools_reload_body),
            FeatureEntry(R.string.features_tools_rotate_title, R.string.features_tools_rotate_body),
            FeatureEntry(R.string.features_tools_open_title, R.string.features_tools_open_body),
            FeatureEntry(R.string.features_tools_password_title, R.string.features_tools_password_body),
        ),
    ),
    PRIVACY(
        R.drawable.privacy_icon,
        R.string.features_topic_privacy,
        R.string.features_topic_privacy_subtitle,
        listOf(
            FeatureEntry(R.string.features_privacy_local_title, R.string.features_privacy_local_body),
            FeatureEntry(R.string.features_privacy_incognito_title, R.string.features_privacy_incognito_body),
            FeatureEntry(R.string.features_privacy_shared_copies_title, R.string.features_privacy_shared_copies_body),
            FeatureEntry(R.string.features_privacy_history_switch_title, R.string.features_privacy_history_switch_body),
            FeatureEntry(R.string.features_privacy_history_screen_title, R.string.features_privacy_history_screen_body),
            FeatureEntry(R.string.features_privacy_clear_title, R.string.features_privacy_clear_body),
            FeatureEntry(R.string.features_privacy_passwords_title, R.string.features_privacy_passwords_body),
            FeatureEntry(R.string.features_privacy_backup_title, R.string.features_privacy_backup_body),
            FeatureEntry(R.string.features_privacy_auto_backup_title, R.string.features_privacy_auto_backup_body),
        ),
    ),
    CUSTOMIZATION(
        R.drawable.ic_color_palate,
        R.string.features_topic_custom,
        R.string.features_topic_custom_subtitle,
        listOf(
            FeatureEntry(R.string.features_custom_colors_title, R.string.features_custom_colors_body),
            FeatureEntry(R.string.features_custom_toolbar_title, R.string.features_custom_toolbar_body),
            FeatureEntry(R.string.features_custom_shortcut_title, R.string.features_custom_shortcut_body),
            FeatureEntry(R.string.features_custom_fullscreen_title, R.string.features_custom_fullscreen_body),
            FeatureEntry(R.string.features_custom_per_document_title, R.string.features_custom_per_document_body),
            FeatureEntry(R.string.features_custom_languages_title, R.string.features_custom_languages_body),
        ),
    ),
}
