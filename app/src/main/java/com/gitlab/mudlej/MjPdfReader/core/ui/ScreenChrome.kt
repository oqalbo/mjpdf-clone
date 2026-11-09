// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.core.ui

import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.pdf.PDF

fun AppCompatActivity.setupScreenChrome() {
    ColorUtil.colorize(this, window, supportActionBar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
}

fun AppCompatActivity.applyIncognitoNightMode() {
    delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES
}

fun AppCompatActivity.clearIncognitoNightMode() {
    delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_UNSPECIFIED
}

fun AppCompatActivity.applyIncognitoOverlay() {
    theme.applyStyle(R.style.IncognitoThemeOverlay, true)
}

fun AppCompatActivity.isIncognitoLaunch(): Boolean =
    intent.getBooleanExtra(PDF.incognitoKey, false)

fun AppCompatActivity.applyIncognitoNightModeFromIntent() {
    if (isIncognitoLaunch()) {
        applyIncognitoNightMode()
    }
}

fun AppCompatActivity.applyIncognitoOverlayFromIntent() {
    if (isIncognitoLaunch()) {
        applyIncognitoOverlay()
    }
}
