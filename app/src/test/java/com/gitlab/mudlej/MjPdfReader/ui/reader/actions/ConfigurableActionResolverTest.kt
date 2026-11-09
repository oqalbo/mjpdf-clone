// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.actions

import com.gitlab.mudlej.MjPdfReader.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigurableActionResolverTest {

    @Test
    fun missingFileBlocksSearchButAllowsDocumentIndependentActions() {
        val state = ResolverState().apply { hasFile = false }

        assertFalse(state.resolver.perform("search"))
        assertEquals(emptyList<String>(), state.calls)
        val allowedIds = listOf("open_local", "open_online", "settings", "about", "incognito")
        for (id in allowedIds) {
            assertTrue(id, state.resolver.perform(id))
        }
        assertEquals(allowedIds, state.calls)
    }

    @Test
    fun searchRunsOnceAfterAFileBecomesAvailable() {
        val state = ResolverState().apply { hasFile = false }
        assertFalse(state.resolver.perform("search"))

        state.hasFile = true

        assertTrue(state.resolver.perform("search"))
        assertEquals(listOf("search"), state.calls)
    }

    @Test
    fun readingDirectionAndDualPageVisibilityFollowScrollOrientation() {
        val state = ResolverState()

        assertFalse(state.action("reading_direction").visible)
        assertTrue(state.action("dual_page").visible)

        state.horizontal = true

        assertTrue(state.action("reading_direction").visible)
        assertFalse(state.action("dual_page").visible)

        state.hasFile = false
        for (horizontal in listOf(false, true)) {
            state.horizontal = horizontal
            assertFalse(state.action("reading_direction").visible)
            assertFalse(state.action("dual_page").visible)
        }
    }

    @Test
    fun navigationVisibilityFollowsEachHistoryCapabilityAndFileAvailability() {
        val state = ResolverState()

        for (hasFile in listOf(false, true)) {
            for (back in listOf(false, true)) {
                for (forward in listOf(false, true)) {
                    for (history in listOf(false, true)) {
                        state.hasFile = hasFile
                        state.back = back
                        state.forward = forward
                        state.history = history
                        val context = "file=$hasFile, back=$back, forward=$forward, history=$history"
                        assertEquals(context, hasFile && back, state.action("nav_back").visible)
                        assertEquals(context, hasFile && forward, state.action("nav_forward").visible)
                        assertEquals(context, hasFile && history, state.action("nav_history").visible)
                    }
                }
            }
        }
    }

    @Test
    fun cropMarginsStateChangesTheLabelIconAndCheckmark() {
        val state = ResolverState()

        state.assertAppearance("crop_margins", R.string.crop_margins, R.drawable.ic_crop_margins, false)
        state.cropMargins = true
        state.assertAppearance("crop_margins", R.string.show_margins, R.drawable.ic_show_margins, true)
    }

    @Test
    fun dualPageStateChangesTheLabelAndCheckmark() {
        val state = ResolverState()

        state.assertAppearance("dual_page", R.string.dual_page_mode_title, R.drawable.ic_dual_page, false)
        state.dualPage = true
        state.assertAppearance("dual_page", R.string.dual_page_off_action, R.drawable.ic_dual_page, true)
    }

    @Test
    fun darkThemeStateChangesTheLabelIconAndCheckmark() {
        val state = ResolverState()

        state.assertAppearance("switch_theme", R.string.switch_to_dark_mode, R.drawable.ic_dark_mode, false)
        state.darkTheme = true
        state.assertAppearance("switch_theme", R.string.switch_to_light_mode, R.drawable.ic_light_mode, true)
    }

    @Test
    fun bookmarkedPageStateChangesTheLabelIconAndCheckmark() {
        val state = ResolverState()

        state.assertAppearance("bookmark_page", R.string.add_bookmark, R.drawable.ic_bookmark_outline, false)
        state.bookmarked = true
        state.assertAppearance("bookmark_page", R.string.remove_bookmark, R.drawable.ic_bookmark_filled, true)
    }

    @Test
    fun incognitoStateChangesTheLabelAndCheckmark() {
        val state = ResolverState()

        state.assertAppearance("incognito", R.string.incognito, R.drawable.ic_incognito, false)
        state.incognito = true
        state.assertAppearance("incognito", R.string.incognito_exit, R.drawable.ic_incognito, true)
    }

    @Test
    fun specialControlsAndUnknownActionsNeverRunAHandler() {
        val state = ResolverState()

        for (id in listOf("brightness", "auto_scroll", "toggle_labels", "none", "bogus")) {
            assertFalse(id, state.resolver.perform(id))
        }
        assertEquals(emptyList<String>(), state.calls)
    }

    @Test
    fun everyConfiguredSurfaceActionHasAResolverEntry() {
        val state = ResolverState()
        val surfaces = mapOf(
            "toolbar" to ConfigurableAction.toolbarActions.filterNot { it == ConfigurableAction.NONE },
            "shortcut bar" to ConfigurableAction.shortcutBarActions,
            "full screen overlay" to ConfigurableAction.dynamicFullScreenOverlayActions,
        )

        for ((surface, actions) in surfaces) {
            for (action in actions) {
                assertNotNull("$surface: ${action.id}", state.resolver.action(action.id))
            }
        }
    }

    private class ResolverState {
        var hasFile = true
        var horizontal = false
        var cropMargins = false
        var dualPage = false
        var darkTheme = false
        var back = false
        var forward = false
        var history = false
        var bookmarked = false
        var incognito = false
        val calls = mutableListOf<String>()

        val resolver = ConfigurableActionResolver(
            hasFile = { hasFile },
            horizontalScrollEnabled = { horizontal },
            cropMarginsEnabled = { cropMargins },
            dualPageEnabled = { dualPage },
            isPdfDarkTheme = { darkTheme },
            canNavigateBack = { back },
            canNavigateForward = { forward },
            canShowNavigationHistory = { history },
            isCurrentPageBookmarked = { bookmarked },
            isIncognito = { incognito },
            handlers = ConfigurableActionResolver.Handlers(
                toggleFullscreen = { calls.add("fullscreen") },
                exitFullscreen = { calls.add("exit_fullscreen") },
                rotate = { calls.add("rotate") },
                toggleHorizontalLock = { calls.add("horizontal_lock") },
                readingDirection = { calls.add("reading_direction") },
                toggleZoomLock = { calls.add("zoom_lock") },
                toggleCropMargins = { calls.add("crop_margins") },
                toggleDualPage = { calls.add("dual_page") },
                screenshot = { calls.add("screenshot") },
                switchTheme = { calls.add("switch_theme") },
                navigateBack = { calls.add("nav_back") },
                navigateForward = { calls.add("nav_forward") },
                showNavigationHistory = { calls.add("nav_history") },
                reload = { calls.add("reload") },
                openLocal = { calls.add("open_local") },
                openOnline = { calls.add("open_online") },
                search = { calls.add("search") },
                goToPage = { calls.add("go_to_page") },
                extractText = { calls.add("extract_text") },
                textMode = { calls.add("text_mode") },
                share = { calls.add("share") },
                settings = { calls.add("settings") },
                fileMetadata = { calls.add("file_metadata") },
                about = { calls.add("about") },
                tableOfContents = { calls.add("table_of_contents") },
                toggleBookmark = { calls.add("bookmark_page") },
                userBookmarks = { calls.add("user_bookmarks") },
                userNotes = { calls.add("user_notes") },
                userHighlights = { calls.add("user_highlights") },
                linksInFile = { calls.add("links_in_file") },
                print = { calls.add("print") },
                addSignature = { calls.add("add_signature") },
                toggleIncognito = { calls.add("incognito") },
            ),
        )

        fun action(id: String): ConfiguredAction = requireNotNull(resolver.action(id))

        fun assertAppearance(id: String, titleRes: Int, iconRes: Int, checked: Boolean) {
            val action = action(id)
            assertEquals("$id label", titleRes, action.titleRes)
            assertEquals("$id icon", iconRes, action.iconRes)
            assertEquals("$id checked", checked, action.checked)
        }
    }
}
