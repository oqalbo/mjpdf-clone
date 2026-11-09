// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.privacy

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.core.debug.AnnotationTestHooks
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId

import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class IncognitoAnnotationTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = PrivacyReader()

    @After
    fun resetHooks() {
        runCatching { AnnotationTestHooks.reset() }
        runCatching { AnnotationTestHooks.clearSaveFaultMode(TestEnvironment.targetContext) }
    }

    @Test
    @TestId("PRIV-30")
    @TestDuration(DurationCategory.SHORT)
    fun aHighlightMadeInIncognitoIsAppliedOnScreenButNeverJournaled() {
        PrivacyFixtures.file(PrivacyFixtures.NORMAL)

        ActivityScenario.launch<MainActivity>(PrivacyReader.incognitoViewIntent(PrivacyFixtures.NORMAL)).use {
            reader.awaitReady()
            reader.awaitIdentity()
            val documentUri = reader.documentUri()

            reader.createHighlightOnCurrentPage()
            reader.awaitDirtyFab(visible = true)

            val afterCreation = highlightGroups(0)
            assertEquals(
                "the incognito highlight must exist as a real annotation group",
                1,
                afterCreation,
            )

            reader.jumpToPageIndex(AWAY_PAGE_INDEX)
            reader.jumpToPageIndex(0)
            reader.reader.awaitFullQualityRender(Await.LONG_TIMEOUT_MS)

            assertEquals(
                "the highlight must survive a re render of page 1 inside the session",
                1,
                highlightGroups(0),
            )
            PrivacyStores.drainJournalWriter(documentUri)
            assertEquals(
                "no journal may be written for an incognito edit",
                emptyList<String>(),
                PrivacyStores.journalFiles().map { it.name },
            )
            assertTrue(
                "the unsaved annotations indicator must stay visible",
                reader.isDirtyFabVisible(),
            )
        }
    }

    @Test
    @TestId("PRIV-38")
    fun aSignaturePlacedInIncognitoWorksButDoesNotPersistTheTemplate() {
        PrivacyFixtures.file(PrivacyFixtures.NORMAL)
        assertFalse(
            "the signature template must be absent before this test runs",
            PrivacyStores.signatureFile.exists(),
        )

        ActivityScenario.launch<MainActivity>(PrivacyReader.incognitoViewIntent(PrivacyFixtures.NORMAL)).use {
            reader.awaitReady()
            reader.awaitIdentity()
            val generationBefore = reader.reader.readState().pageGeneration ?: 0

            reader.openSignatureDialog()
            reader.drawSignatureStroke()
            reader.tapSignaturePlace()

            val pending = Await.untilValue(
                "the pending stamp placement is reported",
                Await.LONG_TIMEOUT_MS,
                read = { TestEnvironment.onMainThread { readerDebug().dumpPendingStamp() } },
            ) { it.isNotBlank() && it != EMPTY_OBJECT }
            assertTrue("the placement dump must describe a real stamp, it read $pending", pending.contains("page"))

            reader.commitPendingStampWithoutSaving()

            reader.reader.awaitState("the page generation advances after the stamp lands", Await.LONG_TIMEOUT_MS) {
                (it.pageGeneration ?: 0) > generationBefore
            }

            PrivacyStores.holdNoSignatureTemplate()
            assertFalse(
                "a template drawn during an incognito session must not be written to disk",
                PrivacyStores.signatureFile.exists(),
            )
            assertEquals(
                "the journal stays off for an incognito signature",
                emptyList<String>(),
                PrivacyStores.journalFiles().map { it.name },
            )
        }

        PrivacyStores.holdNoRecords()
        assertEquals("an incognito signing session must record nothing", 0, PrivacyStores.recordCount())

        ActivityScenario.launch<MainActivity>(PrivacyReader.viewIntent(PrivacyFixtures.SECOND)).use {
            reader.awaitReady()
            reader.awaitIdentity()
            assertFalse(
                "opening a different document normally must find no remembered template",
                PrivacyStores.signatureFile.exists(),
            )
        }
    }

    private fun highlightGroups(pageIndex: Int): Int =
        JSONArray(readerDebug().dumpPageHighlights(pageIndex)).length()

    private fun readerDebug() = TestEnvironment.onMainThread {
        (TestEnvironment.resumedActivityOfType(MainActivity::class.java) as? MainActivity)
            ?.annotationDebug()
            ?: throw ConditionTimeoutException("No resumed reader debug target")
    }

    private companion object {
        const val AWAY_PAGE_INDEX = 2
        const val EMPTY_OBJECT = "{}"
    }
}
