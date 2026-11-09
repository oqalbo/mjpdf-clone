// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.security

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.pdf.Link
import com.gitlab.mudlej.MjPdfReader.suite.bookmarks.LinksRobot
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.intake.IntakeDb
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.links.LinksActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class LinkSchemeTest {
    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()
    private val links = LinksRobot()

    @Test
    @TestId("SEC-28")
    fun aFileLinkInADocumentDoesNotOpenTheAppsOwnData() {
        ActivityScenario.launch<MainActivity>(
            SecurityReader.fixtureViewIntent(SecurityFixtures.NORMAL)
        ).use {
            reader.awaitReady()
            IntakeDb.awaitRecordCount(1, Await.LONG_TIMEOUT_MS)
        }
        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        val database = java.io.File(FileTree.dataDirectory, DATABASE_PATH)
        assertTrue("the Room database must exist at ${database.absolutePath}", database.isFile)

        ActivityScenario.launch<MainActivity>(
            SecurityReader.fixtureViewIntent(SecurityFixtures.LINK_SCHEMES)
        ).use {
            reader.awaitReady()
            val documentUri = reader.readState().documentUri

            IntentCapture.start().use { capture ->
                tapLinkOnPage(SecurityFixtures.LINK_FILE_PAGE_INDEX)
                Snackbars.awaitText(string(R.string.no_app_to_open_link))
                capture.holdOutgoingCount(0)
            }

            SecurityReader.holdForegroundPackage(TestEnvironment.targetPackage)
            assertEquals(
                "the reader must not have reopened itself on its own database",
                documentUri,
                reader.readState().documentUri,
            )
        }

        IntakeDb.holdRecordCount(2)
    }

    @Test
    @TestId("SEC-29")
    fun aContentLinkToAnotherAppsProviderIsNotGrantedAccess() {
        val persistedBefore = TestEnvironment.targetContext.contentResolver.persistedUriPermissions
            .map { it.uri.authority }
            .toSet()

        ActivityScenario.launch<MainActivity>(
            SecurityReader.fixtureViewIntent(SecurityFixtures.LINK_SCHEMES)
        ).use {
            reader.awaitReady()

            IntentCapture.start().use { capture ->
                for (pageIndex in CONTENT_PAGE_INDEXES) {
                    tapLinkOnPage(pageIndex)
                }
                capture.holdOutgoingCount(capture.outgoing().size)

                val fired = capture.outgoing()
                assertTrue(
                    "both content links must produce an outgoing view intent, the monitor recorded " +
                        capture.describe(),
                    fired.size >= CONTENT_PAGE_INDEXES.size,
                )
                for (intent in fired) {
                    assertEquals(
                        "DefaultLinkHandler sets no flags at all, so no grant flag may be attached to " +
                            "${intent.data}",
                        0,
                        IntentCapture.grantFlagsOf(intent),
                    )
                }
            }
        }

        val persistedAfter = TestEnvironment.targetContext.contentResolver.persistedUriPermissions
            .map { it.uri.authority }
            .toSet()
        assertEquals(
            "tapping a content link must not persist a grant for its authority",
            persistedBefore,
            persistedAfter,
        )
    }

    @Test
    @TestId("SEC-30")
    @TestDuration(DurationCategory.SHORT)
    fun anIntentSchemeLinkDoesNotLaunchAnArbitraryComponent() {
        ActivityScenario.launch<MainActivity>(
            SecurityReader.fixtureViewIntent(SecurityFixtures.LINK_SCHEMES)
        ).use {
            reader.awaitReady()

            IntentCapture.start().use { capture ->
                tapLinkOnPage(SecurityFixtures.LINK_INTENT_PAGE_INDEX)
                val fired = capture.awaitOutgoingCount(1).first()

                assertEquals("the app must fire a plain view intent", Intent.ACTION_VIEW, fired.action)
                assertEquals(
                    "the app must call Uri.parse and never Intent.parseUri, so the scheme stays intent",
                    INTENT_SCHEME,
                    fired.data?.scheme,
                )
                assertNull(
                    "the encoded component inside the intent URI must never be honoured, it read " +
                        "${fired.component}",
                    fired.component,
                )
                assertNull(
                    "the encoded package inside the intent URI must never be honoured, it read " +
                        "${fired.`package`}",
                    fired.`package`,
                )
                assertTrue(
                    "the encoded extras inside the intent URI must never be honoured, they read " +
                        "${fired.extras?.keySet()}",
                    fired.extras == null || fired.extras?.isEmpty == true,
                )
            }
        }
    }

    @Test
    @TestId("SEC-31")
    fun aJavascriptLinkDoesNotReachAWebView() {
        ActivityScenario.launch<MainActivity>(
            SecurityReader.fixtureViewIntent(SecurityFixtures.LINK_SCHEMES)
        ).use {
            reader.awaitReady()
            assertFalse("the reader must hold no WebView before the tap", hasWebView())

            IntentCapture.start().use {
                tapLinkOnPage(SecurityFixtures.LINK_JAVASCRIPT_PAGE_INDEX)
                Await.assertThroughout(
                    condition = "no WebView appears anywhere in the app's own hierarchy",
                    windowMs = SecurityReader.SETTLE_WINDOW_MS,
                ) { !hasWebView() }
            }
        }
    }

    @Test
    @TestId("SEC-32")
    fun aSixtyFourKilobyteLinkUriDoesNotHangOrTruncateIntoSomethingElse() {
        ActivityScenario.launch<MainActivity>(
            SecurityReader.fixtureViewIntent(SecurityFixtures.LINK_SCHEMES)
        ).use {
            reader.awaitReady()

            SecurityReader.openAction(R.string.links_in_file)
            links.awaitScanFinished()
            val position = Await.untilValue(
                "the Links screen renders the row for the long link within ${LIST_TIMEOUT_MS}ms",
                LIST_TIMEOUT_MS,
                read = { runCatching { links.positionOfUrl(SecurityFixtures.LONG_URI) }.getOrNull() },
            ) { it != null }!!
            assertTrue("the long link must be listed", position >= 0)

            links.pressBack()
            Screens.awaitGone(LinksActivity::class.java, Await.LONG_TIMEOUT_MS)

            IntentCapture.start().use { capture ->
                tapLinkOnPage(SecurityFixtures.LINK_LONG_PAGE_INDEX)
                capture.holdOutgoingCount(capture.outgoing().size)

                val fired = capture.outgoing().firstOrNull()
                if (fired != null) {
                    assertEquals(
                        "an absurdly long link is either opened whole or refused, never silently cut into a " +
                            "different address",
                        SecurityFixtures.LONG_URI,
                        fired.data?.toString(),
                    )
                }
            }

            assertTrue(
                "the reader must stay interactive after a 65,536 character link",
                reader.readStateOrNull() != null,
            )
        }
    }

    @Test
    @TestId("SEC-33")
    fun aLinkUriContainingNewlinesAndNullBytesIsNotSplitIntoASecondTarget() {
        ActivityScenario.launch<MainActivity>(
            SecurityReader.fixtureViewIntent(SecurityFixtures.LINK_SCHEMES)
        ).use {
            reader.awaitReady()

            IntentCapture.start().use { capture ->
                tapLinkOnPage(SecurityFixtures.LINK_CONTROL_CHARACTERS_PAGE_INDEX)
                Snackbars.awaitText(string(R.string.no_app_to_open_link))
                capture.holdOutgoingCount(0)
            }
        }
    }

    @Test
    @TestDuration(DurationCategory.SHORT)
    fun ordinaryWebAndTelephoneLinksStillOpenFromThePage() {
        ActivityScenario.launch<MainActivity>(
            SecurityReader.fixtureViewIntent(SecurityFixtures.LINK_SCHEMES)
        ).use {
            reader.awaitReady()
            IntentCapture.start().use { capture ->
                val pages = listOf(SecurityFixtures.LINK_CONTROL_PAGE_INDEX, SecurityFixtures.LINK_TEL_PAGE_INDEX)
                pages.forEach { tapLinkOnPage(it) }
                val fired = capture.awaitOutgoingCount(pages.size)
                assertEquals(pages.map { SecurityFixtures.LINK_SCHEME_URIS[it] }, fired.map { it.data.toString() })
                assertTrue(fired.all { it.action == Intent.ACTION_VIEW && IntentCapture.grantFlagsOf(it) == 0 })
            }
        }
    }

    @Test
    fun theLinksScreenRejectsLocalFilesAndControlCharacters() {
        ActivityScenario.launch<MainActivity>(
            SecurityReader.fixtureViewIntent(SecurityFixtures.LINK_SCHEMES)
        ).use {
            reader.awaitReady()
            SecurityReader.openAction(R.string.links_in_file)
            links.awaitScanFinished()
            IntentCapture.start().use { capture ->
                val targets = listOf(
                    SecurityFixtures.LINK_SCHEME_URIS[SecurityFixtures.LINK_FILE_PAGE_INDEX],
                    "FiLe:///data/data/com.gitlab.mudlej.MjPdfReader/databases/app-db.db",
                    SecurityFixtures.CONTROL_CHARACTER_URI,
                    "https://example.invalid%0d%0a/smuggled",
                    "https://example.invalid/%00smuggled",
                )
                targets.forEach { url ->
                    TestEnvironment.onMainThread {
                        links.activity().onLinkClicked(Link("unsafe target", url, 1))
                    }
                    assertEquals("unsafe target was dispatched: $url", 0, capture.outgoing().size)
                }
                Snackbars.awaitText(string(R.string.no_app_to_open_link))
                capture.holdOutgoingCount(0)
            }
        }
    }

    private fun tapLinkOnPage(pageIndex: Int) {
        SecurityReader.jumpToPage(reader, pageIndex)
        reader.awaitRenderIdle()
        SecurityReader.tapRect(reader, SecurityFixtures.LINK_SCHEME_RECTS[pageIndex])
    }

    private fun hasWebView(): Boolean = TestEnvironment.onMainThread {
        val activity = TestEnvironment.resumedActivity() ?: return@onMainThread false
        containsWebView(activity.window.decorView)
    }

    private fun containsWebView(view: View): Boolean {
        if (view is WebView) return true
        if (view !is ViewGroup) return false
        for (index in 0 until view.childCount) {
            if (containsWebView(view.getChildAt(index))) return true
        }
        return false
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    private companion object {
        const val DATABASE_PATH = "databases/app-db.db"
        const val INTENT_SCHEME = "intent"
        const val LIST_TIMEOUT_MS = 3_000L

        val CONTENT_PAGE_INDEXES = listOf(
            SecurityFixtures.LINK_CONTENT_STORAGE_PAGE_INDEX,
            SecurityFixtures.LINK_CONTENT_SMS_PAGE_INDEX,
        )
    }
}
