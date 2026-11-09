// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.memory

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.FileDescriptors
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.text.TextModeRobot
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.text_mode.TextModeActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.LONG)
class TextCacheBoundTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()
    private val text = TextModeRobot()

    @Test
    @TestId("MEM-23")
    @TestDuration(DurationCategory.VERY_LONG)
    fun theTextModePageCacheIsBoundedAtTwentyFourPages() {
        ActivityScenario.launch<MainActivity>(
            MemoryReader.fixtureViewIntent(MemoryFixtures.LARGE)
        ).use {
            reader.awaitReady()
            openTextMode()

            val samples = mutableListOf<TextCacheStats>()
            var position = 0
            while (position < PAGES_TO_SCROLL) {
                position += SCROLL_STEP
                scrollTextTo(position)
                if (position % SAMPLE_EVERY_PAGES == 0) {
                    samples += CacheStats.textCacheStats()
                }
            }

            val overLimit = samples.filter { it.entryCount > CACHE_PAGE_LIMIT }
            assertEquals(
                "TextModeContentLoader.cacheText must hold the page text cache at or below " +
                    "$CACHE_PAGE_LIMIT entries. These samples did not: $overLimit",
                emptyList<TextCacheStats>(),
                overLimit,
            )
            assertTrue(
                "the cache must have filled past the protected window at some point, otherwise no eviction " +
                    "ran and the bound was met without being tested. The samples were $samples",
                samples.any { it.entryCount >= CACHE_PAGE_LIMIT - PROTECTED_MARGIN },
            )

            val first = text.firstVisiblePosition()
            val last = TestEnvironment.onMainThread { text.layoutManager().findLastVisibleItemPosition() }
            val protectedRange = ((first - PREFETCH_DISTANCE).coerceAtLeast(0)..
                (last + PREFETCH_DISTANCE).coerceAtMost(MemoryFixtures.LARGE_PAGES - 1)).toList()

            Await.until(
                "every page in the protected window $protectedRange has finished loading into the cache",
                Await.VERY_LONG_TIMEOUT_MS,
                pollMs = POLL_MS,
            ) { CacheStats.textCacheStats().pageKeys.containsAll(protectedRange) }

            val finalStats = CacheStats.textCacheStats()
            val missing = protectedRange - finalStats.pageKeys
            assertEquals(
                "the visible window extended by $PREFETCH_DISTANCE on each side is protected from eviction " +
                    "in cacheText, so every one of those pages must still be cached. These were not: $missing",
                emptyList<Int>(),
                missing,
            )
            val stale = finalStats.pageKeys.filter { it < first - MAX_TRAILING_PAGES }
            assertEquals(
                "a page more than $MAX_TRAILING_PAGES positions behind the first visible position cannot " +
                    "be protected, so it must have been evicted. These survived: $stale",
                emptyList<Int>(),
                stale,
            )
            assertTrue(
                "the final cache must be at or below $CACHE_PAGE_LIMIT entries, it held $finalStats",
                finalStats.entryCount <= CACHE_PAGE_LIMIT,
            )
        }
    }

    @Test
    @TestId("MEM-31")
    fun leavingTextModeReleasesThePageTextCache() {
        val fixture = MemoryFixtures.file(MemoryFixtures.LARGE)

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
            reader.awaitReady()
            val beforeTextMode = Heap.settledJavaHeap()

            openTextMode()
            var position = 0
            while (position < MEM31_PAGES) {
                position += SCROLL_STEP
                scrollTextTo(position)
            }
            val loaded = CacheStats.textCacheStats()
            assertTrue(
                "text mode must have filled its cache before the release is measured, it held $loaded",
                loaded.entryCount >= CACHE_PAGE_LIMIT - PROTECTED_MARGIN,
            )

            val collected = ReferenceQueue<android.app.Activity>()
            val activity = WeakReference(
                TestEnvironment.resumedActivityOfType(TextModeActivity::class.java)
                    ?: throw AssertionError("Text mode is not resumed, so there is nothing to release"),
                collected,
            )

            TestEnvironment.onMainThread { activity.get()?.finish() }
            Screens.awaitGone(TextModeActivity::class.java, Await.LONG_TIMEOUT_MS)
            Await.until("no text mode content loader remains registered", Await.LONG_TIMEOUT_MS) {
                CacheStats.liveTextLoaders() == 0
            }
            Heap.clearFrameworkActivityRetention()
            Await.untilCollected(activity, collected)

            val afterTextMode = Heap.settledJavaHeap()
            assertTrue(
                Heap.describe("the heap after leaving text mode", beforeTextMode, afterTextMode, RELEASE_BAND),
                Heap.withinBand(beforeTextMode, afterTextMode, RELEASE_BAND),
            )

            MemoryReader.awaitDescriptorCount(fixture, 1)
            MemoryReader.holdDescriptorCount(fixture, 1)
            assertEquals(
                "only the reader's own document may remain open once text mode is gone",
                1,
                FileDescriptors.countFor(fixture),
            )
        }

        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        MemoryReader.awaitDescriptorCount(fixture, 0)
    }

    private fun openTextMode() {
        MemoryReader.openAction(R.string.text_mode)
        Screens.awaitResumed(TextModeActivity::class.java, Await.LONG_TIMEOUT_MS)
        text.awaitPageText(0, Await.VERY_LONG_TIMEOUT_MS)
        assertTrue(
            "Join Paragraphs defaults on, which is what puts extraction on the char metrics path this test " +
                "is written against, so exactly one loader must be live",
            CacheStats.liveTextLoaders() == 1,
        )
    }

    private fun scrollTextTo(position: Int) {
        text.goToPage(position)
        text.awaitPageCounter("$position/${MemoryFixtures.LARGE_PAGES}", Await.VERY_LONG_TIMEOUT_MS)
        text.awaitPageText(position - 1, Await.VERY_LONG_TIMEOUT_MS)
    }

    private companion object {
        const val CACHE_PAGE_LIMIT = 24
        const val PREFETCH_DISTANCE = 2
        const val PROTECTED_MARGIN = 6
        const val MAX_TRAILING_PAGES = 30
        const val PAGES_TO_SCROLL = 120
        const val MEM31_PAGES = 60
        const val SCROLL_STEP = 5
        const val SAMPLE_EVERY_PAGES = 20
        const val POLL_MS = 250L
        const val RELEASE_BAND = 0.25
    }
}
