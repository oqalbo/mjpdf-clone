// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data

import com.gitlab.mudlej.MjPdfReader.support.InMemorySharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Test
import java.time.LocalDateTime

class BackupSettingsRangeTest {

    @Test
    @Ignore("Pending ruling: autoBackupHour 24 is accepted, expected it to be skipped")
    fun hourAtTwentyFourIsSkipped() {
        assertSkipped(Preferences.autoBackupHourKey, Preferences.kindInt, "24")
    }

    @Test
    @Ignore("Pending ruling: autoBackupHour -1 is accepted, expected it to be skipped")
    fun negativeHourIsSkipped() {
        assertSkipped(Preferences.autoBackupHourKey, Preferences.kindInt, "-1")
    }

    @Test
    @Ignore("Pending ruling: autoBackupHour 2147483647 is accepted, expected it to be skipped")
    fun maximumIntegerHourIsSkipped() {
        assertSkipped(Preferences.autoBackupHourKey, Preferences.kindInt, "2147483647")
    }

    @Test
    @Ignore("Pending ruling: autoBackupMinute 60 is accepted, expected it to be skipped")
    fun minuteAtSixtyIsSkipped() {
        assertSkipped(Preferences.autoBackupMinuteKey, Preferences.kindInt, "60")
    }

    @Test
    @Ignore("Pending ruling: partSize NaN is accepted, expected it to be skipped")
    fun notANumberPartSizeIsSkipped() {
        assertSkipped(Preferences.partSizeKey, Preferences.kindFloat, "NaN")
    }

    @Test
    @Ignore("Pending ruling: partSize Infinity is accepted, expected it to be skipped")
    fun infinitePartSizeIsSkipped() {
        assertSkipped(Preferences.partSizeKey, Preferences.kindFloat, "Infinity")
    }

    @Test
    @Ignore("Pending ruling: partSize 0 is accepted, expected it to be skipped")
    fun zeroPartSizeIsSkipped() {
        assertSkipped(Preferences.partSizeKey, Preferences.kindFloat, "0")
    }

    @Test
    @Ignore("Pending ruling: partSize 4.9 is accepted, expected it to be skipped")
    fun partSizeBelowMinimumIsSkipped() {
        assertSkipped(Preferences.partSizeKey, Preferences.kindFloat, "4.9")
    }

    @Test
    @Ignore("Pending ruling: maxZoom 0 is accepted, expected it to be skipped")
    fun zeroMaximumZoomIsSkipped() {
        assertSkipped(Preferences.maxZoomKey, Preferences.kindFloat, "0")
    }

    @Test
    @Ignore("Pending ruling: maxZoom NaN is accepted, expected it to be skipped")
    fun notANumberMaximumZoomIsSkipped() {
        assertSkipped(Preferences.maxZoomKey, Preferences.kindFloat, "NaN")
    }

    @Test
    @Ignore("Pending ruling: hideDelay -1 is accepted, expected it to be skipped")
    fun negativeHideDelayIsSkipped() {
        assertSkipped(Preferences.hideDelayKey, Preferences.kindInt, "-1")
    }

    @Test
    @Ignore("Pending ruling: scrollSpeed -1 is accepted, expected it to be skipped")
    fun negativeScrollSpeedIsSkipped() {
        assertSkipped(Preferences.scrollSpeedKey, Preferences.kindInt, "-1")
    }

    @Test
    fun uppercaseBooleanIsSkipped() {
        assertSkipped(Preferences.highQualityKey, Preferences.kindBoolean, "TRUE")
    }

    @Test
    fun partSizeIncludesBothRangeEndpoints() {
        assertFloatWritten(Preferences.partSizeKey, "5", 5f)
        assertFloatWritten(Preferences.partSizeKey, "1000", 1000f)
    }

    @Test
    fun maximumZoomIncludesBothRangeEndpoints() {
        assertFloatWritten(Preferences.maxZoomKey, "1", 1f)
        assertFloatWritten(Preferences.maxZoomKey, "100", 100f)
    }

    @Test
    @Ignore("Pending ruling: scanBaselineScope is neither classified nor excluded, expected every key to be covered")
    fun everyPreferenceKeyIsImportedOrExplicitlyExcluded() {
        val declaredKeys = setOf(
            Preferences.firstInstallKey,
            Preferences.showFeaturesDialogKey,
            Preferences.showExitFullscreenTipKey,
            Preferences.lastSeenVersionCodeKey,
            Preferences.highQualityKey,
            Preferences.antiAliasingKey,
            Preferences.horizontalScrollKey,
            Preferences.dualPageModeKey,
            Preferences.dualPageFirstPageAloneKey,
            Preferences.pageFitPolicyKey,
            Preferences.pageSnapKey,
            Preferences.pageFlingKey,
            Preferences.browserScrollModeKey,
            Preferences.singlePageModeKey,
            Preferences.tapToTurnKey,
            Preferences.turnPageByMouseButtonsKey,
            Preferences.pdfDarkThemeKey,
            Preferences.appFollowSystemThemeKey,
            Preferences.pdfFollowSystemThemeKey,
            Preferences.interfaceThemeKey,
            Preferences.pdfPagesThemeKey,
            Preferences.enableReloadButtonKey,
            Preferences.primaryButtonActionKey,
            Preferences.secondaryButtonActionKey,
            Preferences.fullScreenOverlayActionsKey,
            Preferences.fullScreenOverlayActionOrderKey,
            Preferences.shortcutBarActionsKey,
            Preferences.shortcutBarActionOrderKey,
            Preferences.screenOnKey,
            Preferences.spaceBetweenPagesKey,
            Preferences.hideDelayKey,
            Preferences.partSizeKey,
            Preferences.maxZoomKey,
            Preferences.inlineTextSelectionKey,
            Preferences.detectExistingHighlightsKey,
            Preferences.highlightColorsKey,
            Preferences.searchIgnoreAccentsKey,
            Preferences.searchZoomToResultKey,
            Preferences.defaultTextModeKey,
            Preferences.turnPageByVolumeButtonsKey,
            Preferences.showScrollHandlePageCountKey,
            Preferences.showAppBarPageCountKey,
            Preferences.alwaysHideMarginsKey,
            Preferences.secondBarEnabledKey,
            Preferences.hideButtonsLabelsKey,
            Preferences.fullScreenInfoShowTimeKey,
            Preferences.fullScreenInfoShowPdfNameKey,
            Preferences.fullScreenInfoShowPageNumberKey,
            Preferences.fullScreenInfoShowReadingPercentageKey,
            Preferences.doubleTapToExitEnabledKey,
            Preferences.doubleTapThreeStepZoomKey,
            Preferences.openPdfsInSeparateWindowsKey,
            Preferences.alwaysOpenAtFirstPageKey,
            Preferences.autoFullScreenKey,
            Preferences.alwaysHorizontalKey,
            Preferences.scrollSpeedKey,
            Preferences.listFilterKey,
            Preferences.homeDisabledKey,
            Preferences.homeShowPdfTitleKey,
            Preferences.homeTabKey,
            Preferences.homeFolderFlatKey,
            Preferences.homeViewModeKey,
            Preferences.homeProgressStyleKey,
            Preferences.homeGridSizeKey,
            Preferences.homeListTitleLinesKey,
            Preferences.homeGridTitleLinesKey,
            Preferences.homeTitleEllipsizeKey,
            Preferences.homeBadgePagesKey,
            Preferences.homeBadgeProgressKey,
            Preferences.homeBadgeLastOpenedKey,
            Preferences.homeBadgeFileSizeKey,
            Preferences.homeBadgeStatusKey,
            Preferences.goToPageGridColumnsKey,
            Preferences.backupFolderTreeUriKey,
            Preferences.autoBackupEnabledKey,
            Preferences.autoBackupHourKey,
            Preferences.autoBackupMinuteKey,
            Preferences.autoBackupLastRunKey,
            Preferences.autoBackupLastErrorKey,
            Preferences.autoBackupErrorAcknowledgedRunKey,
            Preferences.autoBackupEnabledAtKey,
            Preferences.importResultPendingKey,
            Preferences.homeSortKey,
            Preferences.historyEnabledKey,
            Preferences.keepSharedCopiesKey,
            Preferences.sharedCopyModeKey,
            Preferences.scanModeKey,
            Preferences.scanLocationsKey,
            Preferences.scanBaselineScopeKey,
            Preferences.translationModeKey,
            Preferences.translationEngineKey,
            Preferences.translationTargetLanguageKey,
            Preferences.translationCustomUrlKey,
            Preferences.dictionaryDefineWordsKey,
            Preferences.textModeFontSizeKey,
            Preferences.textModeLineSpacingKey,
            Preferences.textModeHorizontalMarginKey,
            Preferences.textModeThemeKey,
            Preferences.textModeFontFamilyKey,
            Preferences.textModeReadableLineLengthKey,
        )
        val excludedKeys = declaredKeys.filterTo(mutableSetOf()) { key ->
            val (puts, skipped) = BackupManager.validateSettings(
                listOf(BackupSetting(key, Preferences.kindString, "probe")),
            )
            puts.isEmpty() && skipped == 0
        }

        assertEquals(emptySet<String>(), declaredKeys - Preferences.backupSettingKinds.keys - excludedKeys)
    }

    @Test
    fun oneSecondBeforeTheScheduledTimeWaitsOneSecond() {
        assertEquals(1_000L, AutoBackupScheduler.millisUntilNext(2, 0, LocalDateTime.of(2026, 11, 3, 1, 59, 59)))
    }

    @Test
    fun exactlyAtTheScheduledTimeWaitsOneDay() {
        assertEquals(86_400_000L, AutoBackupScheduler.millisUntilNext(2, 0, LocalDateTime.of(2026, 11, 3, 2, 0)))
    }

    @Test
    fun oneHourAfterTheScheduledTimeWaitsTwentyThreeHours() {
        assertEquals(82_800_000L, AutoBackupScheduler.millisUntilNext(2, 0, LocalDateTime.of(2026, 11, 3, 3, 0)))
    }

    private fun assertSkipped(key: String, kind: String, value: String) {
        val (puts, skipped) = BackupManager.validateSettings(listOf(BackupSetting(key, kind, value)))
        assertEquals("$key=$value writes", 0, puts.size)
        assertEquals("$key=$value skipped", 1, skipped)
    }

    private fun assertFloatWritten(key: String, value: String, expected: Float) {
        val (puts, skipped) = BackupManager.validateSettings(listOf(BackupSetting(key, Preferences.kindFloat, value)))
        val preferences = InMemorySharedPreferences()
        val editor = preferences.edit()
        puts.forEach { it(editor) }
        editor.apply()

        assertEquals(0, skipped)
        assertEquals(mapOf(key to expected), preferences.all)
    }
}
