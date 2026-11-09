// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.text_mode

import androidx.appcompat.app.AppCompatActivity
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.databinding.TextModeTypographySheetBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider

class ReflowControls(
    val getJoinParagraphs: () -> Boolean,
    val getDetectHeadings: () -> Boolean,
    val getCodeBlocks: () -> Boolean,
    val onJoinParagraphsChanged: (Boolean) -> Unit,
    val onDetectHeadingsChanged: (Boolean) -> Unit,
    val onCodeBlocksChanged: (Boolean) -> Unit,
    val onReset: () -> Unit,
)

class TextModeTypographyController(
    private val activity: AppCompatActivity,
    private val getSettings: () -> TextModeSettings,
    private val onSettingsChanged: (TextModeSettings) -> Unit,
    private val reflowControls: ReflowControls,
) {

    fun showSheet() {
        val dialog = BottomSheetDialog(activity)
        val sheetBinding = TextModeTypographySheetBinding.inflate(activity.layoutInflater)
        dialog.setContentView(sheetBinding.root)

        syncSheet(sheetBinding)

        sheetBinding.fontSizeSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) = Unit
            override fun onStopTrackingTouch(slider: Slider) {
                onSettingsChanged(getSettings().copy(fontSize = slider.value))
            }
        })
        sheetBinding.lineSpacingSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) = Unit
            override fun onStopTrackingTouch(slider: Slider) {
                onSettingsChanged(getSettings().copy(lineSpacing = slider.value))
            }
        })
        sheetBinding.marginSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) = Unit
            override fun onStopTrackingTouch(slider: Slider) {
                onSettingsChanged(getSettings().copy(horizontalMargin = slider.value.toInt()))
            }
        })
        sheetBinding.readableLineLengthSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (getSettings().readableLineLength != isChecked) {
                onSettingsChanged(getSettings().copy(readableLineLength = isChecked))
            }
        }
        sheetBinding.joinParagraphsSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (reflowControls.getJoinParagraphs() != isChecked) {
                reflowControls.onJoinParagraphsChanged(isChecked)
            }
        }
        sheetBinding.detectHeadingsSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (reflowControls.getDetectHeadings() != isChecked) {
                reflowControls.onDetectHeadingsChanged(isChecked)
            }
        }
        sheetBinding.codeBlocksSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (reflowControls.getCodeBlocks() != isChecked) {
                reflowControls.onCodeBlocksChanged(isChecked)
            }
        }
        configureThemeButton(sheetBinding.systemThemeButton, sheetBinding, ReaderTheme.SYSTEM)
        configureThemeButton(sheetBinding.lightThemeButton, sheetBinding, ReaderTheme.LIGHT)
        configureThemeButton(sheetBinding.sepiaThemeButton, sheetBinding, ReaderTheme.SEPIA)
        configureThemeButton(sheetBinding.darkThemeButton, sheetBinding, ReaderTheme.DARK)
        configureThemeButton(sheetBinding.blackThemeButton, sheetBinding, ReaderTheme.BLACK)
        configureThemeButton(sheetBinding.draculaThemeButton, sheetBinding, ReaderTheme.DRACULA)
        configureFontButton(sheetBinding.sansFontButton, sheetBinding, ReaderFontFamily.SANS)
        configureFontButton(sheetBinding.serifFontButton, sheetBinding, ReaderFontFamily.SERIF)
        configureFontButton(sheetBinding.monoFontButton, sheetBinding, ReaderFontFamily.MONO)
        sheetBinding.resetSettingsButton.setOnClickListener {
            onSettingsChanged(TextModeSettings())
            reflowControls.onReset()
            syncSheet(sheetBinding)
        }
        dialog.show()
    }

    private fun syncSheet(sheetBinding: TextModeTypographySheetBinding) {
        val settings = getSettings()
        sheetBinding.fontSizeSlider.value = settings.fontSize.coerceIn(
            sheetBinding.fontSizeSlider.valueFrom,
            sheetBinding.fontSizeSlider.valueTo,
        )
        sheetBinding.lineSpacingSlider.value = settings.lineSpacing.coerceIn(
            sheetBinding.lineSpacingSlider.valueFrom,
            sheetBinding.lineSpacingSlider.valueTo,
        )
        sheetBinding.marginSlider.value = settings.horizontalMargin.toFloat().coerceIn(
            sheetBinding.marginSlider.valueFrom,
            sheetBinding.marginSlider.valueTo,
        )
        sheetBinding.readableLineLengthSwitch.isChecked = settings.readableLineLength
        sheetBinding.joinParagraphsSwitch.isChecked = reflowControls.getJoinParagraphs()
        sheetBinding.detectHeadingsSwitch.isChecked = reflowControls.getDetectHeadings()
        sheetBinding.codeBlocksSwitch.isChecked = reflowControls.getCodeBlocks()

        val checkedThemeButtonId = themeButtonId(settings.theme)
        listOf(
            sheetBinding.systemThemeButton,
            sheetBinding.lightThemeButton,
            sheetBinding.sepiaThemeButton,
            sheetBinding.darkThemeButton,
            sheetBinding.blackThemeButton,
            sheetBinding.draculaThemeButton,
        ).forEach { button ->
            button.setCheckable(true)
            button.isChecked = button.id == checkedThemeButtonId
        }

        val checkedFontButtonId = fontButtonId(settings.fontFamily)
        listOf(
            sheetBinding.sansFontButton,
            sheetBinding.serifFontButton,
            sheetBinding.monoFontButton,
        ).forEach { button ->
            button.setCheckable(true)
            button.isChecked = button.id == checkedFontButtonId
        }
    }

    private fun configureThemeButton(
        button: MaterialButton,
        sheetBinding: TextModeTypographySheetBinding,
        theme: ReaderTheme,
    ) {
        button.setOnClickListener {
            onSettingsChanged(getSettings().copy(theme = theme))
            syncSheet(sheetBinding)
        }
    }

    private fun configureFontButton(
        button: MaterialButton,
        sheetBinding: TextModeTypographySheetBinding,
        fontFamily: ReaderFontFamily,
    ) {
        button.setOnClickListener {
            onSettingsChanged(getSettings().copy(fontFamily = fontFamily))
            syncSheet(sheetBinding)
        }
    }

    private fun themeButtonId(theme: ReaderTheme): Int {
        return when (theme) {
            ReaderTheme.SYSTEM -> R.id.systemThemeButton
            ReaderTheme.LIGHT -> R.id.lightThemeButton
            ReaderTheme.SEPIA -> R.id.sepiaThemeButton
            ReaderTheme.DARK -> R.id.darkThemeButton
            ReaderTheme.BLACK -> R.id.blackThemeButton
            ReaderTheme.DRACULA -> R.id.draculaThemeButton
        }
    }

    private fun fontButtonId(fontFamily: ReaderFontFamily): Int {
        return when (fontFamily) {
            ReaderFontFamily.SANS -> R.id.sansFontButton
            ReaderFontFamily.SERIF -> R.id.serifFontButton
            ReaderFontFamily.MONO -> R.id.monoFontButton
        }
    }
}
