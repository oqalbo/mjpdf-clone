// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.settings

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsPage
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class SliderSettingsTest : SettingsTestCase() {

    @Test
    @TestId("SET-54")
    fun theZoomCeilingIsWrittenOnlyOnApplyAndIsClampedByTheSliderEnds() {
        launchSettingsOn(SettingsPage.ADVANCED).use {
            openSlider(maxZoomTitle)
            SettingsDialog.dragSliderTo(MIDDLE_FRACTION)
            SettingsDialog.cancel()
            SettingsDialog.awaitDismissed()
            SettingsPrefs.assertUnset(Preferences.maxZoomKey)

            openSlider(maxZoomTitle)
            SettingsDialog.dragSliderTo(MIDDLE_FRACTION)
            val chosen = SettingsDialog.sliderValue()
            SettingsDialog.apply()
            SettingsDialog.awaitDismissed()
            SettingsPrefs.awaitFloat(Preferences.maxZoomKey, chosen.toFloat())
            SettingsScreen.awaitRowSummary(maxZoomTitle) {
                it != null && it.endsWith(SettingsStrings.currentValue(chosen))
            }

            openSlider(maxZoomTitle)
            SettingsDialog.reset()
            SettingsDialog.awaitDismissed()
            SettingsPrefs.awaitFloat(Preferences.maxZoomKey, Preferences.maxZoomDefault)

            openSlider(maxZoomTitle)
            SettingsDialog.dragSliderToFarLeft()
            SettingsDialog.apply()
            SettingsDialog.awaitDismissed()
            SettingsPrefs.awaitFloat(Preferences.maxZoomKey, Preferences.minMaxZoom)

            openSlider(maxZoomTitle)
            SettingsDialog.dragSliderToFarRight()
            SettingsDialog.apply()
            SettingsDialog.awaitDismissed()
            SettingsPrefs.awaitFloat(Preferences.maxZoomKey, Preferences.maxMaxZoom)
        }
    }

    @Test
    @TestId("SET-55")
    fun theRenderingTileSizeCannotBeDraggedOutsideItsSupportedRange() {
        launchSettingsOn(SettingsPage.ADVANCED).use {
            openSlider(partSizeTitle)
            SettingsDialog.dragSliderToFarLeft()
            assertTrue(
                "the value label shows the minimum the writer will store",
                SettingsDialog.sliderValue() == Preferences.minPartSize.toInt(),
            )
            SettingsDialog.apply()
            SettingsDialog.awaitDismissed()
            SettingsPrefs.awaitFloat(Preferences.partSizeKey, Preferences.minPartSize)

            openSlider(partSizeTitle)
            SettingsDialog.dragSliderToFarRight()
            assertTrue(
                "the value label shows the maximum the writer will store",
                SettingsDialog.sliderValue() == Preferences.maxPartSize.toInt(),
            )
            SettingsDialog.apply()
            SettingsDialog.awaitDismissed()
            SettingsPrefs.awaitFloat(Preferences.partSizeKey, Preferences.maxPartSize)
        }
    }

    private fun openSlider(rowTitle: String) {
        SettingsScreen.tapRow(rowTitle)
        SettingsDialog.awaitShown()
        Await.until("the slider dialog binds its seek bar") {
            SettingsDialog.isShowing() &&
                com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment.uiDevice
                    .hasObject(androidx.test.uiautomator.By.clazz("android.widget.SeekBar"))
        }
    }

    private val maxZoomTitle: String get() = SettingsStrings.of(R.string.max_zoom)
    private val partSizeTitle: String get() = SettingsStrings.of(R.string.part_size)

    private companion object {
        const val MIDDLE_FRACTION = 0.5f
    }
}
