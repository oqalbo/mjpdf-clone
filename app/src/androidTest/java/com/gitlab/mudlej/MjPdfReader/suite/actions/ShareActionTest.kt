// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.actions

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.IntentCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.rule.IntentsRule
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment

import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.IntentStubs
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.actions.ConfigurableAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.security.MessageDigest
import com.gitlab.mudlej.MjPdfReader.suite.robot.DocumentPickerRobot

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class ShareActionTest {

    private val suite = SuiteRules.standard()
    private val cleanup = ActionsCleanupRule.forFixtures(ActionsFixtures.NORMAL)
    private val serverRule = ActionsServerRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(suite).around(cleanup).around(serverRule)

    @get:Rule
    val intents = IntentsRule()

    private val act = ActionsRobot()

    @Test
    @TestId("ACT-35")
    fun shareHandsTheReceivingAppARealPdfUnderTheDocumentsName() {
        IntentStubs.stubChooser()
        val contentUri = mediaStoreUriFor(ActionsFixtures.NORMAL)

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(contentUri)).use {
            act.awaitReady()

            act.runFromActionsDialog(ActionLabels.of(ConfigurableAction.SHARE))

            val chooser = ShareIntents.awaitChooser()
            val payload = ShareIntents.payloadOf(chooser)

            assertEquals("the chooser wraps a send intent", Intent.ACTION_SEND, payload.action)
            assertEquals("the shared payload is typed as a pdf", PDF_MIME_TYPE, payload.type)
            val sharedUri = IntentCompat.getParcelableExtra(
                payload,
                Intent.EXTRA_STREAM,
                Uri::class.java,
            )
            assertNotNull("the shared payload must carry a readable document stream", sharedUri)
            assertEquals(
                "the clip data and EXTRA_STREAM must grant the same document",
                sharedUri,
                payload.clipData?.getItemAt(0)?.uri,
            )
            assertArrayEquals(
                "the shared stream bytes must be the document the reader opened",
                sha256(contentUri),
                sha256(sharedUri!!),
            )
            assertTrue(
                "the receiving app must be granted read access",
                payload.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0,
            )
            val clipLabel = payload.clipData?.description?.label?.toString()
            assertNotNull("the payload must carry clip data naming the file", clipLabel)
            assertTrue(
                "the clip data label must name a pdf, it reads \"$clipLabel\"",
                clipLabel!!.endsWith(PDF_SUFFIX, ignoreCase = true),
            )
        }
    }

    @Test
    @TestId("ACT-36")
    @TestDuration(DurationCategory.NORMAL)
    fun anOnlineDocumentCanBeSharedAsItsLinkOrAsTheDownloadedFile() {
        IntentStubs.stubChooser()
        val url = serverRule.servePdf(ONLINE_PATH, ActionsFixtures.NORMAL)

        ActivityScenario.launch<MainActivity>(ActionsRobot.launchWithoutDocument()).use {
            DocumentPickerRobot().awaitShown().cancel()
            act.awaitReaderWithoutDocument()

            act.runFromActionsDialog(ActionLabels.of(ConfigurableAction.OPEN_ONLINE))
            onView(withId(R.id.editText)).inRoot(isDialog())
                .perform(replaceText(url), closeSoftKeyboard())
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            onView(withText(TestEnvironment.targetString(R.string.proceed_anyway))).inRoot(isDialog()).perform(click())

            act.reader().awaitLoaded(Await.LONG_TIMEOUT_MS)
            act.reader().awaitRenderIdle()

            act.runFromActionsDialog(ActionLabels.of(ConfigurableAction.SHARE))

            Dialogs.awaitText(TestEnvironment.targetString(R.string.share_link_option))
            onView(withText(TestEnvironment.targetString(R.string.share_link_option))).inRoot(isDialog())
                .check(matches(isDisplayed()))
            onView(withText(TestEnvironment.targetString(R.string.share_pdf_option))).inRoot(isDialog())
                .check(matches(isDisplayed()))

            onView(withText(TestEnvironment.targetString(R.string.share_link_option))).inRoot(isDialog()).perform(click())

            val payload = ShareIntents.payloadOf(ShareIntents.awaitChooser())
            assertEquals("sharing the link sends plain text", TEXT_MIME_TYPE, payload.type)
            assertEquals(
                "the shared text is the url the document was opened from",
                url,
                payload.getStringExtra(Intent.EXTRA_TEXT),
            )
            val beforePdfShare = Intents.getIntents().size
            act.runFromActionsDialog(ActionLabels.of(ConfigurableAction.SHARE))
            Dialogs.awaitText(TestEnvironment.targetString(R.string.share_pdf_option))
            onView(withText(TestEnvironment.targetString(R.string.share_pdf_option))).inRoot(isDialog()).perform(click())
            val pdfPayload = ShareIntents.payloadOf(ShareIntents.awaitChooser(after = beforePdfShare))
            assertEquals(Intent.ACTION_SEND, pdfPayload.action)
            assertEquals(PDF_MIME_TYPE, pdfPayload.type)
            val stream = IntentCompat.getParcelableExtra(pdfPayload, Intent.EXTRA_STREAM, Uri::class.java)
            assertNotNull("sharing the downloaded PDF provides a stream", stream)
            assertEquals(stream, pdfPayload.clipData?.getItemAt(0)?.uri)
            assertTrue("the recipient receives read access", pdfPayload.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertArrayEquals("the shared PDF contains the downloaded fixture bytes", sha256(ActionsFixtures.uri(ActionsFixtures.NORMAL)), sha256(stream!!))
        }
    }

    @Test
    @TestId("ACT-44")
    fun theShareAffordanceOnTheScreenshotSnackbarSharesTheImageJustCaptured() {
        IntentStubs.stubChooser()

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(ActionsFixtures.uri(ActionsFixtures.NORMAL))).use {
            act.awaitReady()

            act.runFromActionsDialog(ActionLabels.of(ConfigurableAction.SCREENSHOT))
            Snackbars.awaitText(TestEnvironment.targetString(R.string.screenshot_saved))

            val screenshot = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val row = ScreenshotOracle.awaitSingleRow(ActionsFixtures.screenshotPrefix(ActionsFixtures.NORMAL))
                row.uri to row.displayName
            } else {
                val file = ScreenshotOracle.awaitSinglePublicPicture(
                    ActionsFixtures.screenshotPrefix(ActionsFixtures.NORMAL),
                )
                Uri.fromFile(file) to file.name
            }

            onView(withId(com.google.android.material.R.id.snackbar_action)).perform(click())

            val payload = ShareIntents.payloadOf(ShareIntents.awaitChooser())
            assertEquals("the capture is shared as an image", Intent.ACTION_SEND, payload.action)
            assertEquals("the capture is typed as an image", IMAGE_MIME_TYPE, payload.type)
            assertEquals(
                "the shared stream is the row this capture wrote",
                screenshot.first,
                IntentCompat.getParcelableExtra(payload, Intent.EXTRA_STREAM, Uri::class.java),
            )
            assertEquals(
                "the clip data names the screenshot file",
                screenshot.second,
                payload.clipData?.description?.label?.toString(),
            )
        }
    }

    private fun sha256(uri: Uri): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        TestEnvironment.targetContext.contentResolver.openInputStream(uri).use { stream ->
            stream ?: throw ConditionTimeoutException("The shared URI $uri cannot be opened for reading")
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest()
    }

    private fun mediaStoreUriFor(fixtureName: String): Uri {
        val file = ActionsFixtures.file(fixtureName)
        val scanned = Fixtures.scanAndWait(listOf(file))
        return scanned[file.absolutePath]
            ?: throw ConditionTimeoutException(
                "The media scan of ${file.absolutePath} returned no content uri, so this test has no content " +
                    "backed document to open."
            )
    }

    private companion object {
        const val PDF_MIME_TYPE = "application/pdf"
        const val TEXT_MIME_TYPE = "text/plain"
        const val IMAGE_MIME_TYPE = "image/*"
        const val PDF_SUFFIX = ".pdf"
        const val ONLINE_PATH = "/act-36.pdf"
    }
}

object ShareIntents {

    fun awaitChooser(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS, after: Int = 0): Intent = Await.untilNotNull(
        "an ACTION_CHOOSER intent was launched",
        timeoutMs,
    ) {
        Intents.getIntents().drop(after).lastOrNull { it.action == Intent.ACTION_CHOOSER }
    }

    fun payloadOf(chooser: Intent): Intent =
        IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java)
            ?: throw AssertionError(
                "The launched chooser carries no EXTRA_INTENT. Intent.createChooser always wraps the payload " +
                    "there, so a chooser without one did not come from the share path under test."
            )
}
