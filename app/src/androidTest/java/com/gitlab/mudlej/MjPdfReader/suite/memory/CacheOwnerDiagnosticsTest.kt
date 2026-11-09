// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.memory

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.app.Activity
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gitlab.mudlej.MjPdfReader.pdf.PageThumbnailCache
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.ui.text_mode.TextModeContentLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class CacheOwnerDiagnosticsTest {
    @Test
    fun thumbnailClosePublishesAnEmptySnapshotWithoutRemovingAnotherOwner() {
        val before = PageThumbnailCache.debugLiveInstances().size
        val caches = TestEnvironment.onMainThread {
            listOf(
                PageThumbnailCache(Activity(), Uri.EMPTY, null),
                PageThumbnailCache(Activity(), Uri.EMPTY, null),
            )
        }
        var firstCloseRequested = false
        try {
            PageThumbnailCache.debugForgetClosedCache()
            assertEquals(
                "two new thumbnail caches must both register as live owners",
                before + 2,
                PageThumbnailCache.debugLiveInstances().size,
            )
            assertNotNull("a live thumbnail cache must publish its statistics", caches[0].debugCacheStats())
            assertEquals(
                "a thumbnail cache that rendered nothing must hold no entries",
                0,
                caches[0].debugCacheStats()!!.entryCount,
            )
            assertTrue("a thumbnail cache that rendered nothing must hold no keys", caches[0].debugCacheKeys().isEmpty())
            caches[0].close()
            firstCloseRequested = true
            awaitClosed(caches[0])
            assertEquals(
                "closing one thumbnail cache must remove exactly one live owner",
                before + 1,
                PageThumbnailCache.debugLiveInstances().size,
            )
            assertTrue(
                "closing the first thumbnail cache must leave the second one live",
                PageThumbnailCache.debugLiveInstances().any { it === caches[1] },
            )
            val closed = PageThumbnailCache.debugClosedCache()
            assertNotNull("a completed close must publish a closed cache snapshot", closed)
            assertEquals("the closed cache snapshot must report no entries", 0, closed!!.stats.entryCount)
            assertEquals("the closed cache snapshot must report no retained kilobytes", 0, closed.stats.sizeKb)
            assertTrue("the closed cache snapshot must report no keys", closed.keys.isEmpty())

            PageThumbnailCache.debugForgetClosedCache()
            assertNull("forgetting the closed snapshot must clear it", PageThumbnailCache.debugClosedCache())
            assertTrue(
                "forgetting the closed snapshot must not remove the second live cache",
                PageThumbnailCache.debugLiveInstances().any { it === caches[1] },
            )
        } finally {
            if (!firstCloseRequested) caches[0].close()
            caches[1].close()
            caches.forEach { awaitClosed(it) }
            PageThumbnailCache.debugForgetClosedCache()
        }
    }

    @Test
    fun textLoaderCloseRemovesOnlyItsOwnObservation() = TestEnvironment.onMainThread {
        val before = TextModeContentLoader.debugLiveInstances().size
        val activity = AppCompatActivity()
        val first = TextModeContentLoader(activity, RecyclerView(TestEnvironment.targetContext), { false }, { false }, { false })
        val second = TextModeContentLoader(activity, RecyclerView(TestEnvironment.targetContext), { false }, { false }, { false })
        try {
            assertEquals(
                "two new text loaders must both register as live owners",
                before + 2,
                TextModeContentLoader.debugLiveInstances().size,
            )
            assertEquals(
                "a text loader that loaded nothing must hold no cached pages",
                0,
                first.debugTextCacheStats()!!.entryCount,
            )
            assertTrue(
                "a text loader that loaded nothing must hold no page keys",
                first.debugTextCacheStats()!!.pageKeys.isEmpty(),
            )
            first.close()
            assertFalse(
                "a closed text loader must no longer be live",
                TextModeContentLoader.debugLiveInstances().any { it === first },
            )
            assertTrue(
                "closing the first text loader must leave the second one live",
                TextModeContentLoader.debugLiveInstances().any { it === second },
            )
            assertEquals(
                "closing one text loader must remove exactly one live owner",
                before + 1,
                TextModeContentLoader.debugLiveInstances().size,
            )
        } finally {
            if (!first.closing) first.close()
            second.close()
        }
        assertEquals(
            "closing both text loaders must return the live owner count to where it started",
            before,
            TextModeContentLoader.debugLiveInstances().size,
        )
    }

    private fun awaitClosed(cache: PageThumbnailCache) {
        Await.until("the thumbnail cache finishes closing", CLOSE_TIMEOUT_MS, pollMs = CLOSE_POLL_MS) {
            PageThumbnailCache.debugLiveInstances().none { it === cache }
        }
    }

    private companion object {
        const val CLOSE_TIMEOUT_MS = 5_000L
        const val CLOSE_POLL_MS = 10L
    }
}
