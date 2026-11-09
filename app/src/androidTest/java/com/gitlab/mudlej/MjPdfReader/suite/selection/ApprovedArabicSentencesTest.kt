// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.selection

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.graphics.Rect
import android.util.Log
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.baseline.BaselineCapture
import com.gitlab.mudlej.MjPdfReader.suite.baseline.BitmapFiles
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.FailureArtifacts
import com.gitlab.mudlej.MjPdfReader.suite.rules.IntentStubs
import com.gitlab.mudlej.MjPdfReader.suite.text.TextModeRobot
import com.gitlab.mudlej.MjPdfReader.suite.view.ViewUi
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.text.Normalizer

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class ApprovedArabicSentencesTest : SelectionTestCase() {
    private data class PageObservation(
        val pageIndex: Int,
        var copiedText: String? = null,
        var displayedText: String? = null,
        val screenshots: MutableMap<String, File> = linkedMapOf(),
        val errors: MutableMap<String, String> = linkedMapOf(),
        val details: JSONObject = JSONObject(),
    )

    @Test
    @TestId("SEL-62")
    fun theLibrarySentencePreservesItsVowelMarks() = checkSentence(0)

    @Test
    @TestId("SEL-63")
    fun theLanguageSentencePreservesStackedMarks() = checkSentence(1)

    @Test
    @TestId("SEL-64")
    fun theWorkSentencePreservesLamAlefAndHamza() = checkSentence(2)

    @Test
    @TestId("SEL-65")
    fun theChildsQuestionPreservesQuotesAndPunctuation() = checkSentence(3)

    @Test
    @TestId("SEL-66")
    fun theFilenameSentencePreservesMixedDirectionAndDigits() = checkSentence(4)

    private fun checkSentence(index: Int) {
        val source = JSONObject(fixture("arabic-approved-sentences.expected.json").readText())
            .getJSONArray("pages").getJSONObject(index)
        val expected = source.getString("text")
        val hash = MessageDigest.getInstance("SHA-256").digest(expected.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        assertEquals("The source must be the user-approved sentence, not captured app output", SOURCE_HASHES[index], hash)
        val testId = "SEL-${62 + index}"
        val file = fixture("arabic-approved-sentence-${index + 1}.pdf")
        val result = ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(file)).use {
            observePage(index, testId)
        }
        assertTrue("Observation/setup failed, not a text verdict: ${result.errors}", result.errors.isEmpty())
        val verdicts = JSONObject().put("approvedSource", expected)
        val mismatches = listOf("Copy Page Text" to result.copiedText, "Text Mode" to result.displayedText)
            .mapNotNull { (surface, actual) ->
                val matches = canonical(actual) == canonical(expected)
                verdicts.put(surface, JSONObject().put("matches", matches).put("actual", actual ?: JSONObject.NULL))
                if (matches) null else "$surface expected <$expected> but was <$actual>"
            }
        File(FailureArtifacts.directoryFor(testId), "assertions.json").writeText(verdicts.toString(2))
        assertTrue("Sentence ${index + 1}:\n${mismatches.joinToString("\n\n")}", mismatches.isEmpty())
    }

    private fun observePage(index: Int, testId: String): PageObservation {
        val directory = FailureArtifacts.directoryFor(testId)
        val result = PageObservation(index)
        val textMode = TextModeRobot()
        val prefix = "page-${index + 1}"
        fun record(stage: String, action: () -> Unit) {
            runCatching(action).onFailure { error ->
                result.errors[stage] = error.stackTraceToString()
                Log.e(LOG_TAG, "$prefix $stage", error)
            }
        }
        fun capture(name: String) = record(name) {
            val screen = BaselineCapture.screen()
            try {
                result.screenshots[name] = BitmapFiles.write(screen, File(directory, "$prefix-$name.png"))
            } finally {
                screen.recycle()
            }
        }
        fun cleanup(stage: String) = record("$stage-cleanup") {
            repeat(4) {
                if (Dialogs.isAnyShowing() || Dialogs.hasAnyActionTile() || textMode.isShowing()) {
                    TestEnvironment.uiDevice.pressBack()
                    TestEnvironment.uiDevice.waitForIdle()
                    TestEnvironment.waitForIdleSync()
                }
            }
            reader.awaitShown(Await.SHORT_TIMEOUT_MS)
            Await.until("observation dialogs and text mode are closed", Await.SHORT_TIMEOUT_MS) {
                !Dialogs.isAnyShowing() && !Dialogs.hasAnyActionTile() && !textMode.isShowing()
            }
        }

        try {
            record("navigation") {
                awaitReaderReady()
                reader.awaitPageIndex(0)
                reader.awaitMotionSettled()
                awaitReaderReady()
            }
        } finally {
            cleanup("navigation")
        }
        record("pdf-ready") {
            reader.awaitNoSelection()
            Await.until("the PDF scroll handle has faded", Await.SCROLL_HANDLE_TIMEOUT_MS) {
                !ViewUi.scrollHandleVisible()
            }
        }
        capture("pdf-screen")
        record("pdf-page") {
            val state = state()
            result.details.put("readerState", state.raw)
            val bounds = Rect()
            state.pageRect!!.roundOut(bounds)
            result.details.put("pageViewRect", JSONArray(listOf(bounds.left, bounds.top, bounds.right, bounds.bottom)))
            val view = BaselineCapture.view(R.id.pdfView)
            try {
                result.details.put("wholePageVisible", Rect(0, 0, view.width, view.height).contains(bounds))
                val page = BaselineCapture.crop(view, bounds)
                try {
                    result.screenshots["pdf-page"] = BitmapFiles.write(page, File(directory, "$prefix-pdf-page.png"))
                } finally {
                    if (page !== view) page.recycle()
                }
            } finally {
                view.recycle()
            }
        }

        record("clear-clipboard") { IntentStubs.clearClipboard() }
        try {
            record("copy") {
                textMode.openReaderActionsDialog()
                textMode.tapReaderTile(textMode.string(R.string.copy_page_text))
                Dialogs.awaitText("${textMode.string(R.string.selectable_text)} #1", Await.LONG_TIMEOUT_MS)
                capture("copy-dialog")
                onView(withText(textMode.string(R.string.copy_all))).inRoot(isDialog()).perform(click())
                TestEnvironment.waitForIdleSync()
            }
        } finally {
            record("clipboard-read") { result.copiedText = clipboardText() }
            if ("copy-dialog" !in result.screenshots) capture("copy-error-screen")
            cleanup("copy")
        }

        try {
            record("text-mode") {
                textMode.openFromReaderMenu()
                val snapshot = Await.untilValue(
                    "text mode page is bound, visible and finished loading",
                    Await.LONG_TIMEOUT_MS,
                    read = {
                        TestEnvironment.onMainThread {
                            textMode.snapshot(0)?.takeIf {
                                it.position == 0 && textMode.pageProgressVisibility(0) == View.GONE &&
                                    textMode.onItem(0) { view -> view.getGlobalVisibleRect(Rect()) }
                            }
                        }
                    },
                ) { it != null }!!
                result.displayedText = snapshot.pageText?.takeIf { snapshot.pageTextVisible }?.toString()
                result.details.put("textModePosition", snapshot.position)
                    .put("textModeTextVisible", snapshot.pageTextVisible)
                    .put("textModeMessage", textMode.pageMessage(0) ?: JSONObject.NULL)
            }
        } finally {
            capture("text-mode-screen")
            cleanup("text-mode")
        }

        val json = result.details.put("pageIndex", index)
        mapOf("copiedText" to result.copiedText, "displayedText" to result.displayedText).forEach { (surface, text) ->
            val hex = text?.codePoints()?.toArray()?.joinToString(" ") { "U+%04X".format(it) }
            json.put(surface, text ?: JSONObject.NULL).put("${surface}CodePoints", hex ?: JSONObject.NULL)
            val message = "$prefix $surface=<$text>\n$prefix $surface codepoints=<$hex>"
            Log.i(LOG_TAG, message)
        }
        json.put("screenshots", JSONObject(result.screenshots.mapValues { it.value.absolutePath }))
            .put("errors", JSONObject(result.errors))
        record("json-write") { File(directory, "$prefix.json").writeText(json.toString(2)) }
        return result
    }

    private fun canonical(text: String?): String? = text?.let {
        Normalizer.normalize(it.trim(), Normalizer.Form.NFC)
    }

    private companion object {
        const val LOG_TAG = "ApprovedArabicSentences"
        val SOURCE_HASHES = arrayOf(
            "bf90f6ebe9c773ef07a998cd73b36355a0b0088f71febe9ed1730be170186755",
            "cde0a61f3aabd0b6054f6decba74d5e54b63713501948f22843c7fea709cca43",
            "b56a7b29380e10af67db3f04dd615eb1248fccb5331f0d2b7f42dfbc038b6489",
            "4f2d1bff68dbc13f9dd25ba360d6539b2fcd477fda4eb39c506dfb589cbc8c41",
            "34607b8af04310a81dc6f2f3bdfbadc90586f9b1f3769270e28ed024c073c87f",
        )
    }
}
