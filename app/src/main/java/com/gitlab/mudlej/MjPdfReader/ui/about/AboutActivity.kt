// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.about

import android.content.ActivityNotFoundException
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import com.gitlab.mudlej.MjPdfReader.BuildConfig
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.io.emailIntent
import com.gitlab.mudlej.MjPdfReader.core.io.linkIntent
import com.gitlab.mudlej.MjPdfReader.core.io.navIntent
import com.gitlab.mudlej.MjPdfReader.core.ui.AppSnackbar
import com.gitlab.mudlej.MjPdfReader.core.ui.copyToClipboard
import com.gitlab.mudlej.MjPdfReader.core.ui.setupScreenChrome
import com.gitlab.mudlej.MjPdfReader.databinding.AboutRowItemBinding
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityAboutBinding
import com.gitlab.mudlej.MjPdfReader.ui.intro.MainIntroActivity
import com.google.android.material.snackbar.Snackbar

class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupScreenChrome()
        bindHeader()
        bindRows()
    }

    private fun bindHeader() {
        val version = versionText()
        binding.versionChip.text = version
        binding.versionChip.setOnClickListener {
            copyToClipboard(this, getString(R.string.mj_app_name), version)
            AppSnackbar.make(binding.root, getString(R.string.copied), Snackbar.LENGTH_SHORT).show()
        }
        binding.featuredCard.setOnClickListener { openLink(OFFICIAL_SITE_URL) }
    }

    private fun bindRows() {
        bindRow(binding.whatsNewRow, R.drawable.log_icon, R.string.whats_new_title) {
            startActivity(navIntent(applicationContext, WhatsNewActivity::class.java))
        }
        bindRow(binding.appFeaturesRow, R.drawable.ic_awesome, R.string.features_title) {
            startActivity(navIntent(applicationContext, AppFeaturesActivity::class.java))
        }
        bindRow(binding.replayIntroRow, R.drawable.replay_icon, R.string.intro) {
            startActivity(navIntent(applicationContext, MainIntroActivity::class.java))
        }
        bindRow(binding.privacyRow, R.drawable.privacy_icon, R.string.privacy) {
            PrivacyInfoDialog().show(supportFragmentManager, PrivacyInfoDialog.TAG)
        }
        bindRow(binding.licenseRow, R.drawable.license_icon, R.string.myLicense) {
            openLink(LICENSE_URL)
        }
        bindRow(binding.sourceCodeRow, R.drawable.code_icon, R.string.code) {
            openLink(REPO_URL)
        }
        bindRow(binding.librariesRow, R.drawable.lib_icon, R.string.libs) {
            OpenSourceLibrariesDialog().show(supportFragmentManager, OpenSourceLibrariesDialog.TAG)
        }
        bindRow(binding.websiteRow, R.drawable.ic_web, R.string.about_website, AUTHOR_SITE_NAME) {
            openLink(AUTHOR_SITE_URL)
        }
        bindRow(binding.emailRow, R.drawable.email_icon, R.string.about_email, EMAIL_ADDRESS) {
            sendEmail()
        }
        bindRow(binding.gitlabRow, R.drawable.ic_gitlab, R.string.gitlab) {
            openLink(GITLAB_URL)
        }
        bindRow(binding.githubRow, R.drawable.ic_github, R.string.github) {
            openLink(GITHUB_URL)
        }
    }

    private fun bindRow(
        row: AboutRowItemBinding,
        @DrawableRes iconRes: Int,
        @StringRes titleRes: Int,
        subtitle: String? = null,
        onClick: () -> Unit,
    ) {
        row.rowIcon.setImageResource(iconRes)
        row.rowTitle.setText(titleRes)
        if (subtitle != null) {
            row.rowSubtitle.text = subtitle
            row.rowSubtitle.visibility = View.VISIBLE
        }
        row.root.setOnClickListener { onClick() }
    }

    private fun versionText(): String {
        val suffix = if (BuildConfig.DEBUG) "-debug" else ""
        return "Version ${BuildConfig.VERSION_NAME}$suffix"
    }

    private fun openLink(url: String) {
        try {
            startActivity(linkIntent(url))
        } catch (e: ActivityNotFoundException) {
            AppSnackbar.make(binding.root, url, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun sendEmail() {
        try {
            startActivity(emailIntent(EMAIL_ADDRESS, getString(R.string.mj_app_name), versionText()))
        } catch (e: ActivityNotFoundException) {
            AppSnackbar.make(binding.root, EMAIL_ADDRESS, Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        private const val EMAIL_ADDRESS = "mudlej@proton.me"
        private const val AUTHOR_SITE_NAME = "mudlej.com"
        private const val OFFICIAL_SITE_URL = "https://mudlej.com/projects/mj-pdf/"
        private const val AUTHOR_SITE_URL = "https://mudlej.com"
        private const val REPO_URL = "https://gitlab.com/mudlej_android/mj_pdf_reader"
        private const val GITLAB_URL = "https://gitlab.com/mudlej"
        private const val GITHUB_URL = "https://github.com/mudlej"
        private const val LICENSE_URL = "https://gitlab.com/mudlej_android/mj_pdf_reader/-/blob/main/LICENSE"
    }
}
