// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.security

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.bookmarks.LinksRobot
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.FileDescriptors
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.intake.IntakeDialogs
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.state.DocumentLoadState
import com.gitlab.mudlej.MjPdfReader.ui.links.LinksActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class DocumentActionTest {
    private val suite = SuiteRules.standard()
    private val server = SecurityServer()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(suite).around(server)

    private val reader = ReaderRobot()
    private val links = LinksRobot()

    @Test
    @TestId("SEC-23")
    fun anOpenActionUriDoesNotFireOnOpen() {
        val traffic = NetworkWatch.sample()
        server.clearRequestLog()
        val fixture = SecurityStaging.stageWithPort(
            SecurityFixtures.OPENACTION_URI,
            server.port,
            suite.testDirectory.directory(),
        )

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
            reader.awaitReady()
            reader.awaitFullQualityRender()

            server.holdRequestLogEmpty("opening a document whose catalog carries an /OpenAction URI action")
            SecurityReader.holdForegroundPackage(TestEnvironment.targetPackage)
        }

        NetworkWatch.assertUnchangedSince(traffic, "opening a document carrying an /OpenAction URI action")
    }

    @Test
    @TestId("SEC-24")
    @TestDuration(DurationCategory.SHORT)
    fun anEmbeddedFileAttachmentIsNotExtractedOrOffered() {
        val fixture = SecurityFixtures.file(SecurityFixtures.ATTACHMENT)
        val dataBefore = FileTree.snapshot(FileTree.dataDirectory)
        val externalBefore = FileTree.snapshot(FileTree.externalRoot)

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
            reader.awaitReady()
            reader.awaitFullQualityRender()
            val startingPage = reader.readState().page

            val tiles = SecurityReader.actionTileLabels()
            val attachmentTiles = tiles.filter { it.contains(ATTACHMENT_WORD, ignoreCase = true) }
            assertEquals(
                "no reader action may offer to open or extract an embedded attachment, the dialog held $tiles",
                emptyList<String>(),
                attachmentTiles,
            )

            SecurityReader.tapRect(reader, SecurityFixtures.ATTACHMENT_RECT)
            Snackbars.assertNoneWithin()
            assertEquals(
                "tapping a /FileAttachment annotation must not move the reader",
                startingPage,
                reader.readState().page,
            )
        }

        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)

        val newFiles = FileTree.diff(dataBefore, FileTree.snapshot(FileTree.dataDirectory)).added +
            FileTree.diff(externalBefore, FileTree.snapshot(FileTree.externalRoot)).added
        val carryingPayload = newFiles.filter { facts ->
            runCatching { File(facts.path).readBytes().toString(Charsets.ISO_8859_1) }
                .getOrDefault("")
                .contains(ATTACHMENT_MARKER)
        }
        assertEquals(
            "the attachment's bytes must stay inside the document, they were found in $carryingPayload",
            emptyList<FileFacts>(),
            carryingPayload,
        )
    }

    @Test
    @TestId("SEC-25")
    fun aLaunchActionLinkIsANoOp() {
        val childrenBefore = ProcessTree.childrenOfAppProcess()

        for (name in LAUNCH_VARIANTS) {
            val fixture = SecurityFixtures.file(name)

            ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
                reader.awaitReady()
                reader.awaitFullQualityRender()
                val startingPage = reader.readState().page

                SecurityReader.tapRect(reader, SecurityFixtures.LINK_RECT_POINTS)
                SecurityReader.holdForegroundPackage(TestEnvironment.targetPackage)

                assertEquals(
                    "FPDFAction_GetURIPath returns nothing for a non URI action and the tap chain checks link " +
                        "hit testing before edge tap paging, so $name must leave the page where it was",
                    startingPage,
                    reader.readState().page,
                )
                Snackbars.assertNoneWithin()

                SecurityReader.openAction(R.string.links_in_file)
                links.awaitScanFinished()
                assertEquals(
                    "$name carries one Launch action and no URI, so the Links screen must list nothing",
                    0,
                    links.rowCount(),
                )
                links.pressBack()
                Screens.awaitGone(LinksActivity::class.java, Await.LONG_TIMEOUT_MS)
            }
            Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
            FileDescriptors.awaitNoneFor(fixture)
        }

        assertEquals(
            "a Launch action must not fork a process from the app. Before: $childrenBefore. " +
                "After: ${ProcessTree.childrenOfAppProcess()}",
            childrenBefore.size,
            ProcessTree.childrenOfAppProcess().size,
        )
    }

    @Test
    @TestId("SEC-26")
    fun aSubmitFormActionSendsNothing() {
        val traffic = NetworkWatch.sample()
        server.clearRequestLog()
        val since = SecurityLogcat.mark()
        val fixture = SecurityStaging.stageWithPort(
            SecurityFixtures.SUBMITFORM,
            server.port,
            suite.testDirectory.directory(),
        )

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
            reader.awaitReady()
            reader.awaitFullQualityRender()

            SecurityReader.tapRect(reader, SecurityFixtures.SUBMIT_TEXT_RECT)
            IntakeDialogs.awaitShowing(SUBMIT_FIELD_TITLE)
            SecurityReader.typeIntoDialog(SUBMIT_MARKER)
            IntakeDialogs.tap(string(R.string.ok))
            reader.awaitRenderIdle()

            SecurityReader.tapRect(reader, SecurityFixtures.SUBMIT_BUTTON_RECT)
            server.holdRequestLogEmpty("tapping a /SubmitForm push button")
        }

        NetworkWatch.assertUnchangedSince(traffic, "tapping a /SubmitForm push button")
        SecurityLogcat.assertAbsent(SUBMIT_MARKER, since, "a form whose submit action names a server")
    }

    @Test
    @TestId("SEC-27")
    @TestDuration(DurationCategory.LONG)
    fun openingAHostileDocumentSendsNoNetworkTrafficAtAll() {
        val traffic = NetworkWatch.sample()
        server.clearRequestLog()
        val directory = suite.testDirectory.directory()

        for (name in SecurityFixtures.actionFixtures()) {
            val fixture = if (name in PORT_PATCHED_FIXTURES) {
                SecurityStaging.stageWithPort(name, server.port, directory)
            } else {
                SecurityFixtures.file(name)
            }

            ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
                reader.awaitShown()
                val state = reader.awaitLoadSettled(Await.LONG_TIMEOUT_MS)
                if (state.loadState != DocumentLoadState.FAILED && (state.pageCount ?: 0) > 0) {
                    reader.awaitRenderIdle()
                }
                reader.pressBack()
            }
            Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        }

        server.holdRequestLogEmpty("opening every hostile fixture in sequence")
        NetworkWatch.holdUnchanged(traffic, "opening every hostile fixture in sequence")
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    private companion object {
        const val ATTACHMENT_WORD = "attach"
        const val ATTACHMENT_MARKER = "mjsec24-payload"
        const val SUBMIT_FIELD_TITLE = "payload"
        const val SUBMIT_MARKER = "mjsec26marker"

        val LAUNCH_VARIANTS = listOf(
            SecurityFixtures.LAUNCH_ACTION,
            SecurityFixtures.LAUNCH_ACTION_LIB,
        )

        val PORT_PATCHED_FIXTURES = setOf(
            SecurityFixtures.OPENACTION_JS,
            SecurityFixtures.OPENACTION_URI,
            SecurityFixtures.FIELD_JS,
            SecurityFixtures.SUBMITFORM,
        )
    }
}
