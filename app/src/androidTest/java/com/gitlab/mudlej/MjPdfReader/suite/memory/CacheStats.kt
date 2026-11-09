// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.memory

import com.github.barteksc.pdfviewer.PDFView
import com.gitlab.mudlej.MjPdfReader.pdf.PageThumbnailCache
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.ui.home.CoverCache
import com.gitlab.mudlej.MjPdfReader.ui.text_mode.TextModeContentLoader

class MissingDebugHookException(message: String) : AssertionError(message)

data class PagePartStats(val active: Int, val passive: Int, val scaling: Boolean, val cacheSize: Int) {
    val total: Int get() = active + passive
    override fun toString(): String =
        "active $active, passive $passive, total $total, scaling $scaling, bound $cacheSize"
}

data class PreviewStats(
    val entryCount: Int,
    val byteTotal: Long,
    val budgetBytes: Long,
    val poolSizes: Map<Int, Int>,
    val activeBucketWidthPx: Int,
    val perBucketCap: Int,
    val attached: Boolean,
) {
    override fun toString(): String =
        "entries $entryCount, bytes $byteTotal of budget $budgetBytes, pool $poolSizes, " +
            "bucket $activeBucketWidthPx, attached $attached"
}

data class OpenPageStats(
    val openPages: Int,
    val openedPageEntries: Int,
    val renderPageOrder: Int,
    val textOpenedPages: Int,
    val pinnedRenderPage: Int,
    val maxOpenRenderPages: Int,
) {
    override fun toString(): String =
        "openPages $openPages, openedPageEntries $openedPageEntries, renderPageOrder $renderPageOrder, " +
            "textOpenedPages $textOpenedPages, pinned $pinnedRenderPage, cap $maxOpenRenderPages"
}

data class LruStats(val entryCount: Int, val sizeKb: Int, val maxSizeKb: Int) {
    override fun toString(): String = "$entryCount entries, $sizeKb of $maxSizeKb kilobytes"
}

data class TextCacheStats(val entryCount: Int, val pageKeysEldestFirst: List<Int>) {

    val pageKeys: Set<Int> get() = pageKeysEldestFirst.toSet()

    val eldest: Int? get() = pageKeysEldestFirst.firstOrNull()

    override fun toString(): String = "$entryCount pages, eldest first $pageKeysEldestFirst"
}

object CacheStats {

    const val MAX_OPEN_RENDER_PAGES = 12
    const val PREVIEW_POOL_PER_BUCKET_CAP = 4
    const val DEFAULT_CACHE_SIZE = 60

    val THUMBNAIL_WIDTH_BUCKETS = listOf(120, 180, 240, 320, 440, 600)
    val COVER_WIDTH_BUCKETS = listOf(160, 320, 640)

    fun viewerStats(pdfView: PDFView): PDFView.DebugCacheStats = TestEnvironment.onMainThread {
        pdfView.readCacheStats()
            ?: throw MissingDebugHookException(
                "PDFView.readCacheStats returned null. PDFView.enableDebugState(true) must run first. " +
                    "MainActivity enables it in " +
                    "onCreate, so a test that never opened the reader must enable it itself."
            )
    }

    fun pagePartStats(pdfView: PDFView): PagePartStats = viewerStats(pdfView).let {
        PagePartStats(it.activePartCount, it.passivePartCount, it.partCacheScaling, it.partCacheLimit)
    }

    fun currentCacheSize(pdfView: PDFView): Int = viewerStats(pdfView).partCacheLimit

    fun currentPartSize(pdfView: PDFView): Float = viewerStats(pdfView).partSizePx

    fun expectedCacheSizeFor(partSize: Int): Int =
        ((2 * 120 * 256 * 256) / (partSize * partSize)).coerceIn(24, 480)

    fun previewStats(pdfView: PDFView): PreviewStats = viewerStats(pdfView).let { stats ->
        val sizes = LinkedHashMap<Int, Int>()
        stats.previewPoolBucketWidths.forEachIndexed { index, width ->
            sizes[width] = stats.previewPoolBucketSizes.getOrElse(index) { 0 }
        }
        PreviewStats(
            entryCount = stats.previewEntryCount,
            byteTotal = stats.previewBytes,
            budgetBytes = stats.previewMaxBytes,
            poolSizes = sizes,
            activeBucketWidthPx = stats.previewBucketWidthPx,
            perBucketCap = stats.previewPoolPerBucketCap,
            attached = stats.previewAttached,
        )
    }

    fun openPageStats(pdfView: PDFView): OpenPageStats = viewerStats(pdfView).let {
        OpenPageStats(
            it.openPageCount,
            it.openedPageEntryCount,
            it.renderPageCount,
            it.textOpenedPageCount,
            it.pinnedRenderPage,
            it.maxOpenRenderPages,
        )
    }

    fun coverCacheStats(): LruStats {
        val stats = CoverCache.debugCacheStatsIfInitialized()
            ?: throw MissingDebugHookException(
                "CoverCache statistics are unavailable: the cache has not been initialized or this is a release build"
            )
        return LruStats(stats.entryCount, stats.sizeKb, stats.maxSizeKb)
    }

    fun thumbnailCacheStats(): LruStats {
        val stats = PageThumbnailCache.debugLiveInstances().lastOrNull()?.debugCacheStats()
            ?: throw MissingDebugHookException(
                "PageThumbnailCache statistics are unavailable. The owner tracks weak references, so this " +
                    "means no page thumbnail cache is live, either because none was created or because the " +
                    "screen that owned it is gone and the instance was collected. Use " +
                    "thumbnailCacheStatsAfterClose for a reading taken after close."
            )
        return LruStats(stats.entryCount, stats.sizeKb, stats.maxSizeKb)
    }

    fun liveThumbnailCaches(): Int = PageThumbnailCache.debugLiveInstances().size

    fun thumbnailCacheKeys(): Set<String> =
        PageThumbnailCache.debugLiveInstances().lastOrNull()?.debugCacheKeys().orEmpty().toSet()

    fun thumbnailCacheStatsAfterClose(): LruStats {
        val stats = PageThumbnailCache.debugClosedCache()?.stats
            ?: throw MissingDebugHookException(
                "PageThumbnailCache.debugClosedCache returned null, so PageThumbnailCache.close never " +
                    "reached the end of its coroutine. A non null reading is itself the proof that close ran " +
                    "to completion."
            )
        return LruStats(stats.entryCount, stats.sizeKb, stats.maxSizeKb)
    }

    fun forgetClosedThumbnailStats() = PageThumbnailCache.debugForgetClosedCache()

    fun expectedLruMaxSizeKilobytes(): Int = (Heap.maxMemory() / 1024 / 8).toInt()

    fun textCacheStats(): TextCacheStats = TestEnvironment.onMainThread {
        val stats = TextModeContentLoader.debugLiveInstances().lastOrNull()?.debugTextCacheStats()
            ?: throw MissingDebugHookException(
                "TextModeContentLoader statistics are unavailable. The owner tracks weak references and the " +
                    "loader unregisters at the top of close, so this means no text mode loader is live, " +
                    "either because text mode was never opened or because it is already closing."
            )
        TextCacheStats(stats.entryCount, stats.pageKeys)
    }

    fun liveTextLoaders(): Int = TextModeContentLoader.debugLiveInstances().size

    fun previewReuseRecycles(): Int = PDFView.debugPreviewReuseRecycles()

    fun forgetPreviewReuseRecycles() = PDFView.debugForgetPreviewReuseRecycles()

    fun previewDecodeFailures(): Int = PDFView.debugPreviewDecodeFailures()

    fun forgetPreviewDecodeFailures() = PDFView.debugForgetPreviewDecodeFailures()
}
