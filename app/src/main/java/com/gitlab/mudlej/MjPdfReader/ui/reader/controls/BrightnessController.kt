// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.controls

import android.app.Activity
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.SeekBar
import androidx.core.view.isVisible
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding
import com.gitlab.mudlej.MjPdfReader.ui.reader.ReaderViewModel
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

class BrightnessController(
    private val activity: Activity,
    private val binding: ActivityMainBinding,
    private val vm: ReaderViewModel,
) {

    fun toggleControlVisibility() {
        binding.brightnessButton.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        if (binding.brightnessLayout.isVisible) hideControl() else showControl()
    }

    fun hideControl() {
        binding.brightnessLayout.visibility = View.GONE
        binding.brightnessButton.isChecked = false
        vm.isBrightnessClicked = false
    }

    fun showControl() {
        binding.brightnessLayout.visibility = View.VISIBLE
        binding.brightnessButton.isChecked = true
        vm.isBrightnessClicked = true
        syncSliderWithCurrentBrightness()
    }

    private fun brightnessSettingMax(): Int {
        val resourceId = activity.resources.getIdentifier(
            "config_screenBrightnessSettingMaximum", "integer", "android"
        )
        val max = if (resourceId > 0) {
            runCatching { activity.resources.getInteger(resourceId) }.getOrDefault(255)
        } else {
            255
        }
        return max.coerceAtLeast(1)
    }

    private fun syncSliderWithCurrentBrightness() {
        val override = activity.window.attributes.screenBrightness
        val linear = if (override >= 0f) {
            override
        } else {
            val max = brightnessSettingMax()
            val system = Settings.System.getInt(
                activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS, max / 2
            )
            system.toFloat() / max
        }
        val brightness = linearToPercent(linear)
        binding.brightnessSeekBar.progress = brightness
        binding.brightnessPercentage.text = "$brightness%"
    }

    fun attachSeekbarListener() {
        syncSliderWithCurrentBrightness()
        binding.brightnessSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (seekBar == null) return
                // Don't override system's brightness if the user didn't manually asked for it
                if (fromUser) updateBrightness(progress)
            }
        })
    }

    private fun updateBrightness(brightness: Int) {
        binding.brightnessPercentage.text = "$brightness%"
        activity.window.attributes.screenBrightness = percentToLinear(brightness)
        activity.window.attributes = activity.window.attributes // apply it
    }

    companion object {
        internal fun linearToPercent(linear: Float): Int {
            val normalized = linear.coerceIn(0f, 1f) * 12f
            val gamma = if (normalized <= 1f) {
                sqrt(normalized) * GAMMA_R
            } else {
                GAMMA_A * ln(normalized - GAMMA_B) + GAMMA_C
            }
            return (gamma * 100).roundToInt().coerceIn(0, 100)
        }

        internal fun percentToLinear(percent: Int): Float {
            val gamma = percent.coerceIn(0, 100) / 100f
            val normalized = if (gamma <= GAMMA_R) {
                (gamma / GAMMA_R).pow(2)
            } else {
                exp((gamma - GAMMA_C) / GAMMA_A) + GAMMA_B
            }
            return normalized.coerceIn(0f, 12f) / 12f
        }

        private const val GAMMA_R = 0.5f
        private const val GAMMA_A = 0.17883277f
        private const val GAMMA_B = 0.28466892f
        private const val GAMMA_C = 0.55991073f
    }
}
