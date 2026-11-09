// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.localization

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.suite.state.LayoutDirection
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class MirroringTest : LocaleTestCase() {

    @Test
    @TestId("LOC-11")
    fun theHomeScreenMirrorsUnderArabic() {
        useLocale(ARABIC)
        LocaleRecords.seedMany(HOME_RECORDS)

        launchHome().use {
            assertEquals(
                "home must mirror under a right to left locale",
                LayoutDirection.RTL,
                ScreenDirection.require(HomeActivity::class.java, Await.LONG_TIMEOUT_MS),
            )

            val fab = ScreenDirection.boundsOf(HomeActivity::class.java, R.id.openPdfFab)
            assertTrue(
                "the open action must move to the mirrored side, read $fab against a " +
                    "${ScreenDirection.screenWidth()} pixel wide screen",
                ScreenDirection.isInLeftHalf(fab),
            )
        }
    }

    @Test
    @TestId("LOC-12")
    fun theReaderChromeMirrorsUnderArabic() {
        useLocale(ARABIC)

        openReader(LocaleFixtures.NORMAL) { _ ->
            assertEquals(
                "the reader must mirror under a right to left locale",
                LayoutDirection.RTL,
                ScreenDirection.require(MainActivity::class.java, Await.LONG_TIMEOUT_MS),
            )

            LocaleFullscreen.enter()
            LocaleFullscreen.revealPanel()

            val panel = ScreenDirection.boundsOf(MainActivity::class.java, R.id.fullScreenButtonsLayout)
            assertTrue(
                "the fullscreen panel must sit against the mirrored edge, read $panel against a " +
                    "${ScreenDirection.screenWidth()} pixel wide screen",
                panel.right > ScreenDirection.screenWidth() * PANEL_EDGE_FRACTION,
            )

            val state = reader.readState()
            assertEquals("mirroring must not move the reader off its page", 0, state.page)
            assertEquals(
                "mirroring must not change the document",
                LocaleFixtures.NORMAL_PAGES,
                state.pageCount,
            )
        }
    }

    @Test
    @TestId("LOC-13")
    fun theSettingsRowsMirrorUnderArabic() {
        useLocale(ARABIC)

        launchSettings().use {
            assertEquals(
                "settings must mirror under a right to left locale",
                LayoutDirection.RTL,
                ScreenDirection.require(SettingsActivity::class.java, Await.LONG_TIMEOUT_MS),
            )

            val title = LocaleRowGeometry.firstRowTitleBounds(translated(R.string.settings_home))
            val icon = LocaleRowGeometry.leadingIconBoundsFor(translated(R.string.settings_home))
            assertTrue(
                "the leading icon must sit to the right of the row title when mirrored, " +
                    "icon $icon against title $title",
                icon.left > title.right,
            )
        }
    }

    @Test
    @TestId("LOC-14")
    @TestDuration(DurationCategory.NORMAL)
    fun theAnnotationListsAndContentsMirrorUnderArabic() {
        useLocale(ARABIC)

        openReader(LocaleFixtures.HIGHLIGHTS_RICH) { _ ->
            LocaleLists.screens().forEach { screen ->
                LocaleLists.openFromActions(screen)
                assertEquals(
                    "${screen.type.simpleName} must mirror under a right to left locale",
                    LayoutDirection.RTL,
                    ScreenDirection.require(screen.type, Await.LONG_TIMEOUT_MS),
                )
                assertEquals(
                    "${screen.type.simpleName} must carry its Arabic title",
                    translated(screen.titleRes),
                    LocaleLists.titleOf(screen.type),
                )
                LocaleLists.returnToReader()
            }
        }
    }

    @Test
    @TestId("LOC-15")
    fun theFullscreenPanelHidesTowardTheMirroredEdgeUnderArabic() {
        useLocale(ARABIC)
        SettingsSeeder.seed(Preferences.hideDelayKey, LONG_HIDE_DELAY_MS)

        openReader(LocaleFixtures.NORMAL) { _ ->
            LocaleFullscreen.enter()
            LocaleFullscreen.revealPanel()

            assertEquals(
                "the panel must still be on screen when the hide is triggered, or the hide branch does not " +
                    "publish its computed translation target",
                android.view.View.VISIBLE,
                LocaleFullscreen.panelVisibility(),
            )
            LocaleFullscreen.hidePanel()

            val target = LocaleFullscreen.awaitHideTargetTranslationX()
            assertTrue(
                "the panel must target the right edge under a mirrored layout, the computed hide translation " +
                    "was $target",
                target > 0f,
            )
            LocaleFullscreen.awaitPanelGone()
            assertEquals(
                "the translation must be reset once the end action runs",
                0f,
                LocaleFullscreen.panelTranslationX(),
                TRANSLATION_TOLERANCE,
            )
        }
    }

    @Test
    @TestId("LOC-16")
    fun aRightToLeftUiLocaleDoesNotFlipPageTurnDirectionForALeftToRightDocument() {
        useLocale(ARABIC)
        SettingsSeeder.seed(Preferences.horizontalScrollKey, true)
        SettingsSeeder.seed(Preferences.pageSnapKey, true)

        openReader(LocaleFixtures.NORMAL) { _ ->
            val before = reader.readState()
            assertEquals("the document must open on its first page", 0, before.page)
            assertEquals(
                "an English document must resolve left to right whatever the UI locale is",
                false,
                before.readingDirectionRtl,
            )

            reader.swipeForward()

            reader.awaitPageIndex(1, Await.LONG_TIMEOUT_MS)
            assertEquals(
                "the resolved reading direction must still be left to right after the turn",
                false,
                reader.readState().readingDirectionRtl,
            )
        }
    }

    private companion object {
        const val HOME_RECORDS = 3
        const val PANEL_EDGE_FRACTION = 0.75f
        const val TRANSLATION_TOLERANCE = 0.5f
        const val LONG_HIDE_DELAY_MS = 60_000
    }
}
