// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.selection

import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.baseline.BaselineCapture
import com.gitlab.mudlej.MjPdfReader.suite.baseline.BitmapFiles
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.SearchIdle
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.localization.SearchHighlight
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.IntentStubs
import com.gitlab.mudlej.MjPdfReader.suite.search.SearchBar
import com.gitlab.mudlej.MjPdfReader.suite.search.SearchOracle
import com.gitlab.mudlej.MjPdfReader.suite.search.SearchResultsScreen
import com.gitlab.mudlej.MjPdfReader.suite.search.SearchRobot
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.suite.text.TextModeRobot
import com.gitlab.mudlej.MjPdfReader.suite.view.ViewUi
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

@LargeTest
@RunWith(AndroidJUnit4::class)
class ExternalArabicObservationTest {
    @Test
    fun observeExternalArabicPage() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Opt-in external corpus observer", args.containsKey("externalArabicFile"))
        val id = args.getString("externalArabicId")
        val validId = id?.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) == true
        val directory = File(
            "/sdcard/Documents/mjpdf-test-artifacts/external-arabic",
            if (validId) id else "_invalid-arguments",
        )
        val errors = JSONObject()
        val stages = JSONObject()
        val screenshots = JSONObject()
        val pdf = JSONObject().put("ready", false)
        val copy = JSONObject().put("copyAllTapped", false).put("copiedText", JSONObject.NULL)
        val text = JSONObject().put("ready", false).put("displayedText", JSONObject.NULL)
        val search = JSONObject().put("ready", false).put("hitCount", JSONObject.NULL)
            .put("hitPages", JSONObject.NULL).put("cache", JSONObject.NULL)
        val observation = JSONObject().put("schemaVersion", 1)
            .put("kind", "diagnostic-observation-no-semantic-verdict")
            .put("observationComplete", false)
            .put("startedAtEpochMs", System.currentTimeMillis())
            .put("id", id ?: JSONObject.NULL)
            .put("sourcePath", args.getString("externalArabicFile") ?: JSONObject.NULL)
            .put("pageArgument", args.getString("externalArabicPage") ?: JSONObject.NULL)
            .put("query", args.getString("externalArabicQuery") ?: JSONObject.NULL)
            .put("device", Build.FINGERPRINT).put("sdkInt", Build.VERSION.SDK_INT)
            .put("pdf", pdf).put("copy", copy).put("textMode", text).put("search", search)
            .put("stages", stages).put("screenshots", screenshots).put("errors", errors)
        val reader = ReaderRobot()
        val textMode = TextModeRobot()
        val searchRobot = SearchRobot(reader)
        val results = SearchResultsScreen()
        var scenario: ActivityScenario<MainActivity>? = null

        fun error(stage: String, failure: Throwable) {
            errors.put(stage, failure.stackTraceToString())
            Log.e("ExternalArabicObservation", stage, failure)
        }
        fun archive() {
            try {
                check(directory.isDirectory || directory.mkdirs()) { "Cannot create $directory" }
                val pending = File(directory, "observation.json.tmp")
                pending.writeText(observation.toString(2))
                check(pending.renameTo(File(directory, "observation.json"))) { "Cannot replace observation.json" }
            } catch (failure: Throwable) {
                error("json-write", failure)
            }
        }
        fun record(stage: String, action: () -> Unit): Boolean {
            stages.put(stage, "running")
            archive()
            val succeeded = try {
                action()
                stages.put(stage, "complete")
                true
            } catch (failure: Throwable) {
                error(stage, failure)
                stages.put(stage, "error")
                false
            }
            archive()
            return succeeded
        }
        fun capture(name: String) = record(name) {
            val bitmap = BaselineCapture.screen()
            try {
                screenshots.put(name, BitmapFiles.write(bitmap, File(directory, "$name.png")).absolutePath)
            } finally {
                bitmap.recycle()
            }
        }
        fun capturePage(name: String, details: JSONObject) = record(name) {
            val state = reader.readState()
            details.put("readerState", state.raw).put("actualPageIndex", state.page ?: JSONObject.NULL)
            val bounds = Rect()
            checkNotNull(state.pageRect) { "No rendered page rectangle" }.roundOut(bounds)
            val bitmap = BaselineCapture.view(R.id.pdfView)
            try {
                val visible = Rect(0, 0, bitmap.width, bitmap.height)
                details.put("pageViewRect", JSONArray(listOf(bounds.left, bounds.top, bounds.right, bounds.bottom)))
                    .put("wholePageVisible", visible.contains(bounds))
                check(visible.intersect(bounds)) { "Page does not intersect the PDF viewport" }
                details.put("cropViewRect", JSONArray(listOf(visible.left, visible.top, visible.right, visible.bottom)))
                val cropped = BaselineCapture.crop(bitmap, visible)
                try {
                    screenshots.put(name, BitmapFiles.write(cropped, File(directory, "$name.png")).absolutePath)
                } finally {
                    if (cropped !== bitmap) cropped.recycle()
                }
            } finally {
                bitmap.recycle()
            }
        }
        fun cleanup(stage: String) = record("$stage-cleanup") {
            repeat(4) {
                if (Dialogs.isAnyShowing() || Dialogs.hasAnyActionTile() || textMode.isShowing() || results.isShown()) {
                    TestEnvironment.uiDevice.pressBack()
                    TestEnvironment.uiDevice.waitForIdle()
                    TestEnvironment.waitForIdleSync()
                }
            }
            reader.awaitShown(Await.SHORT_TIMEOUT_MS)
            Await.until("observation overlays are closed", Await.SHORT_TIMEOUT_MS) {
                !Dialogs.isAnyShowing() && !Dialogs.hasAnyActionTile() && !textMode.isShowing() && !results.isShown()
            }
        }

        try {
            try {
                Fixtures.ensureAllFilesAccess()
                stages.put("storage", "complete")
            } catch (failure: Throwable) {
                error("storage", failure)
                stages.put("storage", "error")
            }
            record("observation") observe@{
                require(validId) { "externalArabicId must match [A-Za-z0-9][A-Za-z0-9._-]{0,127}" }
                val path = requireNotNull(args.getString("externalArabicFile")) { "externalArabicFile is required" }
                val file = File(path)
                require(file.isAbsolute && file.parentFile == File("/sdcard/Download") &&
                    file.extension.equals("pdf", ignoreCase = true)) {
                    "externalArabicFile must be /sdcard/Download/name.pdf"
                }
                val pageIndex = requireNotNull(args.getString("externalArabicPage")?.toIntOrNull()) {
                    "externalArabicPage must be a zero-based integer"
                }
                require(pageIndex >= 0) { "externalArabicPage must be nonnegative" }
                val query = requireNotNull(args.getString("externalArabicQuery")) { "externalArabicQuery is required" }
                require(query.isNotBlank()) { "externalArabicQuery must not be blank" }
                observation.put("targetPageIndex", pageIndex)
                search.put("requestedQuery", query).put("ignoreAccents", true)

                if (!record("source") {
                    check(file.isFile && file.canRead()) { "Cannot read original device file $file" }
                    val digest = MessageDigest.getInstance("SHA-256")
                    var bytesRead = 0L
                    file.inputStream().buffered().use { stream ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = stream.read(buffer)
                            if (count < 0) break
                            digest.update(buffer, 0, count)
                            bytesRead += count
                        }
                    }
                    observation.put("sourceSha256", digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) })
                        .put("sourceBytesRead", bytesRead)
                }) return@observe
                if (!record("launch") {
                    SettingsSeeder.suppressOnboarding()
                    scenario = ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(file))
                    reader.awaitShown(Await.LONG_TIMEOUT_MS)
                    val loaded = reader.awaitLoaded(Await.VERY_LONG_TIMEOUT_MS)
                    observation.put("pageCount", loaded.pageCount ?: JSONObject.NULL)
                    pdf.put("loadedReaderState", loaded.raw)
                    val pageCount = checkNotNull(loaded.pageCount) { "Reader has no page count" }
                    require(pageIndex < pageCount) { "Target index $pageIndex outside $pageCount pages" }
                }) {
                    capture("launch-error-screen")
                    return@observe
                }
                record("navigation") {
                    textMode.readerGoToPage(pageIndex + 1)
                    reader.awaitPageIndex(pageIndex, Await.LONG_TIMEOUT_MS)
                    reader.awaitMotionSettled(Await.LONG_TIMEOUT_MS)
                    reader.awaitFullQualityRender(Await.VERY_LONG_TIMEOUT_MS)
                    reader.awaitNoSelection()
                    pdf.put("ready", true).put("readerState", reader.readState().raw)
                }
                cleanup("navigation")
                record("pdf-ready") {
                    reader.awaitPageIndex(pageIndex)
                    Await.until("the PDF scroll handle has faded", Await.SCROLL_HANDLE_TIMEOUT_MS) {
                        !ViewUi.scrollHandleVisible()
                    }
                }
                capture("pdf-screen")
                capturePage("pdf-page", pdf)

                record("clear-clipboard") {
                    IntentStubs.clearClipboard()
                    copy.put("clipboardCleared", true)
                }
                record("copy") {
                    reader.awaitPageIndex(pageIndex)
                    SearchBar.awaitGone()
                    textMode.openReaderActionsDialog()
                    textMode.tapReaderTile(textMode.string(R.string.copy_page_text))
                    val title = "${textMode.string(R.string.selectable_text)} #${pageIndex + 1}"
                    Await.until("Copy Page Text shows a dialog or app notice", Await.LONG_TIMEOUT_MS) {
                        Dialogs.hasText(title) || SearchBar.labelOrNull() != null
                    }
                    copy.put("dialogTitle", title).put("dialogShown", Dialogs.hasText(title))
                        .put("notice", SearchBar.labelOrNull() ?: JSONObject.NULL)
                    capture("copy-screen")
                    if (Dialogs.hasText(title)) {
                        onView(withText(textMode.string(R.string.copy_all))).inRoot(isDialog()).perform(click())
                        copy.put("copyAllTapped", true)
                        TestEnvironment.waitForIdleSync()
                    }
                }
                record("clipboard-read") {
                    copy.put("copiedText", IntentStubs.clipboardText() ?: JSONObject.NULL)
                        .put("clipboardLabel", IntentStubs.clipboardLabel() ?: JSONObject.NULL)
                }
                if (!screenshots.has("copy-screen")) capture("copy-error-screen")
                cleanup("copy")

                record("text-mode") {
                    reader.awaitPageIndex(pageIndex)
                    textMode.openFromReaderMenu()
                    val snapshot = Await.untilValue(
                        "target Text Mode item is bound, visible and finished loading",
                        Await.VERY_LONG_TIMEOUT_MS,
                        read = {
                            TestEnvironment.onMainThread {
                                textMode.snapshot(pageIndex)?.also { snapshot ->
                                    text.put("position", snapshot.position)
                                        .put("boundText", snapshot.pageText?.toString() ?: JSONObject.NULL)
                                        .put("pageTextVisible", snapshot.pageTextVisible)
                                        .put("message", textMode.pageMessage(pageIndex) ?: JSONObject.NULL)
                                        .put("progressVisibility", textMode.pageProgressVisibility(pageIndex) ?: JSONObject.NULL)
                                }?.takeIf {
                                    it.position == pageIndex && textMode.pageProgressVisibility(pageIndex) == View.GONE &&
                                        textMode.onItem(pageIndex) { view -> view.getGlobalVisibleRect(Rect()) }
                                }
                            }
                        },
                    ) { it != null }!!
                    text.put("displayedText", snapshot.pageText?.takeIf { snapshot.pageTextVisible }?.toString() ?: JSONObject.NULL)
                        .put("ready", true)
                }
                capture("text-mode-screen")
                cleanup("text-mode")

                var fileHash: String? = null
                record("search-submit") {
                    check(!textMode.isShowing()) { "Text Mode is still open" }
                    reader.awaitShown(Await.LONG_TIMEOUT_MS)
                    fileHash = reader.awaitFileHashResolved(Await.VERY_LONG_TIMEOUT_MS)
                    search.put("appFileHash", fileHash)
                    SearchIdle.clear()
                    searchRobot.openSearchDialog()
                    if (!searchRobot.ignoreAccentsChecked()) searchRobot.toggleIgnoreAccents()
                    search.put("ignoreAccents", searchRobot.ignoreAccentsChecked())
                    searchRobot.typeQuery(query)
                    search.put("queryFieldText", searchRobot.queryFieldText())
                    capture("search-query-screen")
                    searchRobot.submit()
                    if (searchRobot.isTooShortQueryDialogShown()) {
                        search.put("proceededPastShortQueryWarning", true)
                        searchRobot.tapProceedAnyway()
                    }
                    searchRobot.awaitSearchDialogGone()
                }
                record("search-finished") {
                    val hash = checkNotNull(fileHash) { "No resolved document identity for search" }
                    Await.until("app search finishes for this document and query", Await.VERY_LONG_TIMEOUT_MS) {
                        val progress = SearchIdle.progress()
                        search.put("progress", progress?.let {
                            JSONObject().put("fileHash", it.fileHash ?: JSONObject.NULL).put("query", it.query)
                                .put("ignoreAccents", it.ignoreAccents).put("pagesScanned", it.pagesScanned)
                                .put("pageCount", it.pageCount).put("finished", it.finished)
                        } ?: JSONObject.NULL)
                        SearchOracle.session(hash, query, true) != null ||
                            (progress?.fileHash == hash && progress.query == query.trim() &&
                                progress.ignoreAccents && progress.finished)
                    }
                    TestEnvironment.waitForIdleSync()
                    search.put("ready", true)
                }
                record("search-cache") {
                    val session = SearchOracle.session(checkNotNull(fileHash), query, true)
                    search.put("cachePresent", session != null)
                    checkNotNull(session) { "Finished search has no cache; complete hit observations unavailable" }
                    val hits = session.hits.sortedBy { it.resultIndex }
                    search.put("hitCount", hits.size).put("hitPages", JSONArray(hits.map { it.pageNumber }))
                        .put("hitPageIndexes", JSONArray(hits.map { it.pageNumber - 1 }))
                        .put("cache", JSONObject().put("fileHash", session.key.fileHash).put("query", session.key.query)
                            .put("ignoreAccents", session.key.ignoreAccents).put("hits", JSONArray(hits.map {
                                JSONObject().put("pageNumber", it.pageNumber).put("originalIndex", it.originalIndex)
                                    .put("resultIndex", it.resultIndex).put("text", it.text).put("inputStart", it.inputStart)
                                    .put("inputEnd", it.inputEnd).put("expanded", it.expanded)
                            })))
                }
                record("search-ui") {
                    search.put("label", SearchBar.labelOrNull() ?: JSONObject.NULL)
                        .put("counter", SearchBar.counterText() ?: JSONObject.NULL)
                        .put("readerState", reader.readState().raw)
                }
                capture("search-screen")
                record("search-first-match") {
                    val count = search.optInt("hitCount", -1)
                    if (count == 0) {
                        search.put("firstMatch", JSONObject.NULL)
                        return@record
                    }
                    SearchBar.awaitShown(Await.LONG_TIMEOUT_MS)
                    SearchBar.tapLabel()
                    results.awaitShown()
                    results.awaitSearchFinished()
                    results.awaitItemCountMatchesTitle()
                    search.put("resultsTitle", results.title()).put("resultsRowCount", results.itemCount())
                    capture("search-results-screen")
                    if (results.itemCount() == 0) return@record
                    results.scrollToPosition(0)
                    val first = JSONObject().put("navigationReady", false)
                        .put("rowText", results.rowText(0) ?: JSONObject.NULL)
                        .put("pageLabel", results.pageLabel(0) ?: JSONObject.NULL)
                    search.put("firstMatch", first)
                    try {
                        results.tapRow(0)
                        results.awaitGone()
                        reader.awaitShown(Await.LONG_TIMEOUT_MS)
                        val firstPage = search.optJSONArray("hitPageIndexes")?.optInt(0, -1) ?: -1
                        if (firstPage >= 0) {
                            first.put("requestedPageIndex", firstPage)
                            reader.awaitPageIndex(firstPage, Await.LONG_TIMEOUT_MS)
                            SearchBar.awaitCounterPosition(1)
                        }
                        reader.awaitMotionSettled(Await.LONG_TIMEOUT_MS)
                        reader.awaitFullQualityRender(Await.VERY_LONG_TIMEOUT_MS)
                        first.put("navigationReady", true)
                    } finally {
                        record("search-highlight-state") {
                            first.put("label", SearchBar.labelOrNull() ?: JSONObject.NULL)
                                .put("counter", SearchBar.counterText() ?: JSONObject.NULL)
                                .put("highlightBoundsRaw", SearchHighlight.readRaw())
                        }
                        capture("search-first-match-screen")
                        capturePage("search-first-match-page", first)
                    }
                }
                if (!screenshots.has("search-first-match-screen") && search.optInt("hitCount", -1) != 0) {
                    capture("search-first-match-error-screen")
                }
                cleanup("search")
                record("search-dismiss") {
                    if (SearchBar.isShown()) {
                        SearchBar.tapDone()
                        SearchBar.awaitGone()
                    }
                }
            }
        } finally {
            record("activity-close") { scenario?.close() }
            observation.put("finishedAtEpochMs", System.currentTimeMillis())
                .put("observationComplete", errors.length() == 0)
            archive()
        }
        if (errors.length() != 0) {
            throw AssertionError("Observation/setup errors only; no Arabic semantic verdict. See $directory/observation.json\n$errors")
        }
    }
}
