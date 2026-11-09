// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.macrobenchmark

import android.content.Intent
import android.net.Uri
import java.io.File

object BenchmarkTargets {
    const val PACKAGE = "com.gitlab.mudlej.MjPdfReader"

    const val LAUNCHER = "$PACKAGE.Launcher"
    const val READER = "$PACKAGE.ui.reader.MainActivity"
    const val GO_TO_PAGE = "$PACKAGE.ui.gotopage.GoToPageActivity"
    const val TEXT_MODE = "$PACKAGE.ui.text_mode.TextModeActivity"

    const val PDF_MIME = "application/pdf"

    const val FILE_PATH_KEY = "filePathKey"
    const val PAGE_NUMBER_KEY = "pageNumber"

    const val FIXTURE_DIR = "/sdcard/Download"
    const val PERF_LIBRARY_DIR = "/sdcard/Documents/perf-library"

    const val SMALL = "perf-small.pdf"
    const val SCANNED_40 = "perf-scanned-40.pdf"
    const val PAGES_500 = "perf-500p.pdf"
    const val HIGHLIGHTS_ONE_PAGE = "perf-500-highlights-one-page.pdf"

    const val HOME_TABS = "homeTabs"
    const val HOME_PAGER = "homePager"
    const val PDF_VIEW = "pdfView"
    const val PAGE_GRID = "pageGrid"
    const val TEXT_PAGES = "textPagesRecyclerView"

    fun path(name: String): String = "$FIXTURE_DIR/$name"

    fun file(name: String): File = File(path(name))

    fun launcherIntent(): Intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setClassName(PACKAGE, LAUNCHER)

    fun viewIntent(name: String): Intent = Intent(Intent.ACTION_VIEW)
        .setClassName(PACKAGE, READER)
        .setDataAndType(Uri.fromFile(file(name)), PDF_MIME)
        .addCategory(Intent.CATEGORY_DEFAULT)

    fun goToPageIntent(name: String, pageIndex: Int = 0): Intent = Intent(Intent.ACTION_MAIN)
        .setClassName(PACKAGE, GO_TO_PAGE)
        .putExtra(FILE_PATH_KEY, Uri.fromFile(file(name)).toString())
        .putExtra(PAGE_NUMBER_KEY, pageIndex)

    fun textModeIntent(name: String, pageIndex: Int = 0): Intent = Intent(Intent.ACTION_MAIN)
        .setClassName(PACKAGE, TEXT_MODE)
        .putExtra(FILE_PATH_KEY, Uri.fromFile(file(name)).toString())
        .putExtra(PAGE_NUMBER_KEY, pageIndex)
}
