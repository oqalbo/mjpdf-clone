// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.controls

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding
import com.gitlab.mudlej.MjPdfReader.core.ui.ColorUtil
import com.gitlab.mudlej.MjPdfReader.ui.settings.ThemeChoiceStrip
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class PdfThemeController(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val pref: Preferences,
) {

    fun configureTheme() {
        ColorUtil.colorize(activity, activity.window, activity.supportActionBar)
        val color = ColorUtil.getBarColor(activity)
        binding.secondBarScrollView.setBackgroundColor(color)

        applyPdfThemeToView(effectivePdfDarkTheme(), reloadPages = false)

        val appNightMode = interfaceNightMode(pref)
        if (AppCompatDelegate.getDefaultNightMode() != appNightMode) {
            AppCompatDelegate.setDefaultNightMode(appNightMode)
        }
    }

    fun effectivePdfDarkTheme(): Boolean = effectivePdfDarkTheme(activity, pref)

    fun switchPdfTheme(hasFile: () -> Boolean, onThemeChanged: () -> Unit) {
        if (pref.getPdfPagesTheme() == Preferences.themeSystem) {
            showFollowsSystemDialog(onThemeChanged)
        }
        else if (hasFile()) {
            val mode = if (pref.getPdfDarkTheme()) Preferences.themeLight else Preferences.themeDark
            setPdfThemeMode(mode)
            onThemeChanged()
        }
    }

    private fun showFollowsSystemDialog(onThemeChanged: () -> Unit) {
        val view = activity.layoutInflater.inflate(R.layout.dialog_pdf_theme_mode, null)
        val group = view.findViewById<MaterialButtonToggleGroup>(R.id.theme_choice_group)
        ThemeChoiceStrip.bind(group, pref.getPdfPagesTheme()) { mode ->
            setPdfThemeMode(mode)
            onThemeChanged()
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.dark_theme_for_pdf)
            .setMessage(R.string.pdf_theme_follows_system_dialog)
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun setPdfThemeMode(mode: String) {
        if (pref.getPdfPagesTheme() == mode) {
            return
        }
        pref.setPdfPagesTheme(mode)
        applyPdfThemeToView(effectivePdfDarkTheme(), reloadPages = true)
    }

    private fun applyPdfThemeToView(darkTheme: Boolean, reloadPages: Boolean) {
        binding.pdfView.setNightMode(darkTheme)
        if (!darkTheme) {
            binding.pdfView.setBackgroundColor(Preferences.pdfDarkBackgroundColor)
        } else {
            binding.pdfView.setBackgroundColor(Preferences.pdfLightBackgroundColor)
        }
        if (reloadPages) {
            binding.pdfView.invalidate()
        }
    }

    companion object {
        fun effectivePdfDarkTheme(context: Context, pref: Preferences): Boolean {
            return when (pref.getPdfPagesTheme()) {
                Preferences.themeSystem -> isSystemDarkTheme(context)
                Preferences.themeDark -> true
                else -> false
            }
        }

        fun interfaceNightMode(pref: Preferences): Int {
            return when (pref.getInterfaceTheme()) {
                Preferences.themeSystem -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                Preferences.themeDark -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_NO
            }
        }

        private fun isSystemDarkTheme(context: Context): Boolean {
            return when (context.applicationContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                Configuration.UI_MODE_NIGHT_YES -> true
                else -> false
            }
        }
    }
}
