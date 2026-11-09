// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.security

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.FileDescriptors
import com.gitlab.mudlej.MjPdfReader.suite.core.HeapSampler
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.intake.IntakeDb
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.state.DocumentLoadState
import com.gitlab.mudlej.MjPdfReader.suite.state.RenderActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class MalformedDocumentTest {
    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()

    @Test
    @TestId("SEC-01")
    fun aTruncatedDocumentSettlesDeterministicallyAndReleasesItsDescriptor() {
        val fixture = SecurityFixtures.truncated(rules.testDirectory.directory())
        assertEquals(
            "no descriptor may resolve to the fixture before it is opened",
            0,
            FileDescriptors.countFor(fixture),
        )

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
            reader.awaitShown()
            val state = reader.awaitLoadSettled(SETTLE_TIMEOUT_MS)

            if (state.loadState == DocumentLoadState.FAILED) {
                Snackbars.awaitText(string(R.string.file_opening_error))
                IntakeDb.holdEmpty()
            } else {
                assertTrue(
                    "a truncated document that pdfium repairs must not claim the whole document's page count, " +
                        "because the objects for the cut pages are gone, it reported ${state.pageCount}",
                    (state.pageCount ?: 0) in 1 until SecurityFixtures.NORMAL_PAGES,
                )
                reader.awaitRenderIdle()
            }
        }

        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        FileDescriptors.awaitNoneFor(fixture)
    }

    @Test
    @TestId("SEC-02")
    @TestDuration(DurationCategory.NORMAL)
    fun aCorruptCrossReferenceTableProducesTheSameOutcomeEveryTime() {
        val fixture = SecurityFixtures.file(SecurityFixtures.BAD_XREF)
        val outcomes = mutableListOf<String>()

        repeat(REPEAT_RUNS) { run ->
            ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
                reader.awaitShown()
                val state = reader.awaitLoadSettled(SETTLE_TIMEOUT_MS)
                outcomes += "${state.loadState}/${state.pageCount}"

                if (state.loadState == DocumentLoadState.FAILED) {
                    Snackbars.awaitText(string(R.string.file_opening_error))
                } else {
                    assertEquals(
                        "the repaired document must report the page objects that are actually present",
                        SecurityFixtures.BAD_XREF_PAGES as Int?,
                        state.pageCount,
                    )
                    reader.awaitRenderIdle()
                    SecurityReader.jumpToPage(reader, SecurityFixtures.BAD_XREF_PAGES - 1)
                    reader.awaitRenderIdle()
                    Snackbars.assertNoneWithin()
                }
            }
            Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
            if (run < REPEAT_RUNS - 1) {
                FileDescriptors.awaitNoneFor(fixture)
            }
        }

        assertEquals(
            "a damaged index must repair to the same document every time, it produced $outcomes",
            1,
            outcomes.toSet().size,
        )
    }

    @Test
    @TestId("SEC-03")
    fun aDanglingObjectReferenceLeavesAPageBlankRatherThanCrashing() {
        val fixture = SecurityFixtures.file(SecurityFixtures.DANGLING_REF)

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
            reader.awaitShown()
            val state = reader.awaitLoadSettled(SETTLE_TIMEOUT_MS)

            if (state.loadState == DocumentLoadState.FAILED) {
                Snackbars.awaitText(string(R.string.file_opening_error))
            } else {
                assertTrue(
                    "a document that loaded must hold at least one descriptor on the fixture, otherwise the " +
                        "release assertion below would pass without proving anything",
                    FileDescriptors.countFor(fixture) >= 1,
                )
                SecurityReader.jumpToPage(reader, SecurityFixtures.DANGLING_REF_BROKEN_PAGE_INDEX)
                val settled = reader.awaitRenderIdle()
                assertEquals(
                    "the page whose content stream is missing must still settle rather than render forever",
                    RenderActivity.IDLE,
                    settled.render,
                )
            }
        }

        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        FileDescriptors.awaitNoneFor(fixture)
    }

    @Test
    @TestId("SEC-04")
    fun aCircularObjectReferenceTerminatesWithinABoundedTimeAndHeap() {
        val fixture = SecurityFixtures.file(SecurityFixtures.CIRCULAR_REF)
        val sampler = HeapSampler.start()

        try {
            ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
                reader.awaitShown()
                reader.awaitLoadSettled(CYCLE_TIMEOUT_MS)
            }
        } finally {
            sampler.close()
        }

        assertTrue(
            "a file built to make the parser chase its own tail must not grow the heap without bound: " +
                sampler.describe(),
            sampler.growthBytes() <= HeapSampler.BUDGET_128_MB,
        )
        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        FileDescriptors.awaitNoneFor(fixture)
    }

    @Test
    @TestId("SEC-05")
    fun aDeeplyNestedObjectGraphIsBoundedAndLeavesTheReaderInteractive() {
        val fixture = SecurityFixtures.file(SecurityFixtures.DEEP_NEST)

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
            reader.awaitShown()
            val state = reader.awaitLoadSettled(CYCLE_TIMEOUT_MS)

            if (state.loadState != DocumentLoadState.FAILED && (state.pageCount ?: 0) > 0) {
                reader.awaitRenderIdle()
            }

            Await.until(
                "the reader is still resumed and answering on the main thread after the nested document settled",
                Await.SHORT_TIMEOUT_MS,
            ) { TestEnvironment.onMainThread { SecurityReader.activityOrNull() != null } }
        }

        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        FileDescriptors.awaitNoneFor(fixture)
    }

    @Test
    @TestId("SEC-10")
    fun aCyclicPageTreeTerminatesWithAFinitePageCount() {
        val fixture = SecurityFixtures.file(SecurityFixtures.CYCLIC_PAGETREE)
        val sampler = HeapSampler.start()
        var pageCount = 0

        try {
            ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
                reader.awaitShown()
                val state = reader.awaitLoadSettled(CYCLE_TIMEOUT_MS)
                pageCount = state.pageCount ?: 0

                if (state.loadState != DocumentLoadState.FAILED && pageCount > 0) {
                    assertTrue(
                        "a self referential page tree must not produce an unbounded page count, it reported " +
                            pageCount,
                        pageCount <= MAX_PLAUSIBLE_PAGE_COUNT,
                    )
                    for (pageIndex in 0 until pageCount) {
                        SecurityReader.jumpToPage(reader, pageIndex)
                        reader.awaitRenderIdle()
                    }
                }
            }
        } finally {
            sampler.close()
        }

        assertTrue(
            "walking a cyclic page tree must not grow the heap without bound: ${sampler.describe()}",
            sampler.growthBytes() <= HeapSampler.BUDGET_256_MB,
        )
        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        FileDescriptors.awaitNoneFor(fixture)
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    private companion object {
        const val SETTLE_TIMEOUT_MS = 10_000L
        const val CYCLE_TIMEOUT_MS = 15_000L
        const val REPEAT_RUNS = 3
        const val MAX_PLAUSIBLE_PAGE_COUNT = 64
    }
}
