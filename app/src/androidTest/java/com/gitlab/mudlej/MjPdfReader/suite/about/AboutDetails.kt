// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.about

import com.gitlab.mudlej.MjPdfReader.BuildConfig

object AboutDetails {

    const val EMAIL_ADDRESS = "mudlej@proton.me"
    const val AUTHOR_SITE_NAME = "mudlej.com"
    const val REPO_URL = "https://gitlab.com/mudlej_android/mj_pdf_reader"

    fun versionText(): String = "Version ${BuildConfig.VERSION_NAME}" + if (BuildConfig.DEBUG) "-debug" else ""
}
