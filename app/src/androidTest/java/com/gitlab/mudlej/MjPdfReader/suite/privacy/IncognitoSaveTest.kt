// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.privacy

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.VerificationModes.times
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.rule.IntentsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.debug.AnnotationTestHooks
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.ControlledPickerDocument
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.StubSharerProvider
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.IntentStubs
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class IncognitoSaveTest {

    private val suite = SuiteRules.standard()
    private val intents = IntentsRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(suite).around(intents)

    private val reader = PrivacyReader()

    @After
    fun cleanUp() {
        runCatching { AnnotationTestHooks.clearSaveFaultMode(TestEnvironment.targetContext) }
        runCatching { AnnotationTestHooks.reset() }
        runCatching { StubSharerProvider.clear() }
        savedCopies().forEach { runCatching { it.delete() } }
    }

    @Test
    @TestId("PRIV-32")
    fun savingAHighlightedDocumentWhileIncognitoWritesThePdfButNoDatabaseRows() {
        PrivacyFixtures.file(PrivacyFixtures.NORMAL)
        val destinationName = uniqueDestinationName()
        val destination = prepareDestination(destinationName)

        ActivityScenario.launch<MainActivity>(PrivacyReader.incognitoViewIntent(PrivacyFixtures.NORMAL)).use {
            reader.awaitReady()
            reader.awaitIdentity()
            val sourceUri = reader.documentUri()

            reader.createHighlightOnCurrentPage()
            reader.awaitDirtyFab(visible = true)

            saveToNewCopy(destination)

            Snackbars.awaitText(string(R.string.highlights_saved), Await.LONG_TIMEOUT_MS)

            PrivacyStores.drainJournalWriter(sourceUri)
            assertEquals(
                "no journal may exist for the source of an incognito save",
                emptyList<String>(),
                PrivacyStores.journalFiles().map { it.name },
            )
        }

        Await.until("the saved copy appears on disk", Await.LONG_TIMEOUT_MS) {
            destination.isFile && destination.length() > 0L
        }
        assertTrue("the saved copy must not be empty", destination.length() > 0L)

        PrivacyStores.holdNoRecords()
        assertEquals("saving in incognito must record no document", 0, PrivacyStores.recordCount())
        assertEquals(
            "the chosen destination must not be remembered for next time",
            0,
            PrivacyStores.saveDestinationCount(),
        )

        ActivityScenario.launch<MainActivity>(
            ReaderRobot.viewIntent(destination)
        ).use {
            reader.awaitReady()
            reader.awaitIdentity()
            val target = TestEnvironment.onMainThread {
                (TestEnvironment.resumedActivityOfType(MainActivity::class.java) as? MainActivity)
                    ?.annotationDebug()
                    ?: throw ConditionTimeoutException("No resumed reader debug target")
            }
            assertEquals(
                "the saved copy must carry the highlight made in incognito",
                1,
                JSONArray(target.dumpPageHighlights(0)).length(),
            )
        }
    }

    @Test
    @TestId("PRIV-33")
    fun aFailedSaveWhileIncognitoWritesNoRescueCopy() {
        PrivacyFixtures.file(PrivacyFixtures.NORMAL)
        AnnotationTestHooks.setSaveFaultMode(TestEnvironment.targetContext, AnnotationTestHooks.FAULT_FAIL_WRITE)

        ActivityScenario.launch<MainActivity>(PrivacyReader.incognitoViewIntent(PrivacyFixtures.NORMAL)).use {
            reader.awaitReady()
            reader.awaitIdentity()
            reader.createHighlightOnCurrentPage()
            reader.awaitDirtyFab(visible = true)

            saveToNewCopy(prepareDestination(uniqueDestinationName()))
            awaitFailureDialog(string(R.string.annotation_save_failed_title))

            assertTrue(
                "the generic failure message must be used, not the variant naming a rescue file",
                dialogShows(string(R.string.annotation_save_failed_message)),
            )
            dismissDialog()

            PrivacyStores.holdNoRescueCopy()
            assertEquals(
                "the recovery copy must stay unwritten in incognito",
                emptyList<String>(),
                PrivacyStores.rescueFiles().map { it.name },
            )
        }

        ActivityScenario.launch<MainActivity>(PrivacyReader.viewIntent(PrivacyFixtures.NORMAL)).use {
            reader.awaitReady()
            reader.awaitIdentity()
            reader.createHighlightOnCurrentPage()
            reader.awaitDirtyFab(visible = true)

            saveToNewCopy(prepareDestination(uniqueDestinationName()), expectedPickerLaunches = 2)
            awaitFailureDialog(string(R.string.annotation_save_failed_title))
            dismissDialog()

            val rescued = Await.untilValue(
                "the same forced failure without incognito does write a rescue copy",
                Await.LONG_TIMEOUT_MS,
                read = { PrivacyStores.rescueFiles() },
            ) { it.size == 1 }
            assertEquals("the control pass must leave exactly one rescue copy", 1, rescued.size)
        }
    }

    @Test
    @TestId("PRIV-34")
    @TestDuration(DurationCategory.SHORT)
    fun savingWhileIncognitoStillTakesAPersistableWriteGrantOnTheDestination() {
        PrivacyFixtures.file(PrivacyFixtures.NORMAL)
        assertEquals(
            "the persisted grant list must start empty",
            emptyList<String>(),
            PrivacyStores.persistedUris(),
        )
        val destinationName = uniqueDestinationName()
        val destination = prepareDestination(destinationName)

        ActivityScenario.launch<MainActivity>(PrivacyReader.incognitoViewIntent(PrivacyFixtures.NORMAL)).use {
            reader.awaitReady()
            reader.awaitIdentity()
            reader.createHighlightOnCurrentPage()
            reader.awaitDirtyFab(visible = true)

            saveToNewCopy(destination)
            Snackbars.awaitText(string(R.string.highlights_saved), Await.LONG_TIMEOUT_MS)
        }

        val grants = Await.untilValue(
            "the incognito save leaves one persisted write grant",
            Await.LONG_TIMEOUT_MS,
            read = { PrivacyStores.persistedPermissions() },
        ) { it.size == 1 }
        assertEquals("exactly one persisted grant must be held", 1, grants.size)
        assertTrue(
            "the held grant must be a write grant on the chosen destination, it is ${grants.single().uri}",
            grants.single().isWritePermission,
        )
        assertEquals(
            "the destination must still not be remembered in the database",
            0,
            PrivacyStores.saveDestinationCount(),
        )
    }

    private fun saveToNewCopy(destination: File, expectedPickerLaunches: Int = 1) {
        val destinationUri = ControlledPickerDocument.writable(destination)
        IntentStubs.stubUriResultFor(Intent.ACTION_CREATE_DOCUMENT, destinationUri)
        reader.saveAsNewCopy()
        intended(hasAction(Intent.ACTION_CREATE_DOCUMENT), times(expectedPickerLaunches))
    }

    private fun prepareDestination(destinationName: String): File =
        File(com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures.externalRoot, "Documents/$destinationName")
            .apply {
                parentFile?.mkdirs()
                writeBytes(ByteArray(0))
            }

    private fun awaitFailureDialog(title: String) {
        Await.until("the save failure dialog titled \"$title\" is shown", Await.LONG_TIMEOUT_MS) {
            TestEnvironment.uiDevice.hasObject(androidx.test.uiautomator.By.text(title))
        }
    }

    private fun dialogShows(text: String): Boolean =
        TestEnvironment.uiDevice.hasObject(androidx.test.uiautomator.By.text(text))

    private fun dismissDialog() {
        TestEnvironment.uiDevice.pressBack()
        TestEnvironment.waitForIdleSync()
    }

    private fun savedCopies(): List<File> =
        File(com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures.externalRoot, "Documents")
            .listFiles()
            ?.filter { it.isFile && it.name.startsWith(DESTINATION_PREFIX) }
            .orEmpty()

    private fun uniqueDestinationName(): String = "$DESTINATION_PREFIX${System.currentTimeMillis()}.pdf"

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    private companion object {
        const val DESTINATION_PREFIX = "priv-save-qxzv-"
    }
}
