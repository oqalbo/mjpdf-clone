// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.controls

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.descendants
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.barteksc.pdfviewer.PDFView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.debug.ReaderTestHooks
import com.gitlab.mudlej.MjPdfReader.core.debug.SearchTestHooks
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding
import com.gitlab.mudlej.MjPdfReader.ui.intro.MainIntroActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.DocumentState
import com.gitlab.mudlej.MjPdfReader.ui.reader.ReaderViewModel
import com.gitlab.mudlej.MjPdfReader.ui.reader.navigation.ReaderHistoryManager
import com.gitlab.mudlej.MjPdfReader.ui.reader.navigation.SearchNavigationController
import com.gitlab.mudlej.MjPdfReader.ui.search.SearchSessionCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class ReaderHookRegressionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var binding: ActivityMainBinding
    private lateinit var host: FrameLayout
    private lateinit var activity: Activity
    private lateinit var preferences: Preferences
    private var scenario: ActivityScenario<MainIntroActivity>? = null
    private var search: SearchNavigationController? = null
    private var debugWasEnabled: Boolean? = null

    @Before
    fun setUp() {
        scenario = ActivityScenario.launch(MainIntroActivity::class.java)
        scenario!!.onActivity {
            activity = it
            host = FrameLayout(it).apply { id = android.R.id.content }
            binding = ActivityMainBinding.inflate(it.layoutInflater, host, true)
            it.setContentView(host)
            val prefs = it.getSharedPreferences("ReaderHookRegressionTest", Context.MODE_PRIVATE)
            prefs.edit().clear().commit()
            preferences = Preferences(prefs)
            debugWasEnabled = PDFView.isDebugStateEnabled()
            PDFView.enableDebugState(true)
            SearchTestHooks.reset()
            ReaderTestHooks.reset()
        }
        await("Attached reader host") {
            host.isAttachedToWindow && binding.pdfView.width > 0 && binding.pdfView.height > 0
        }
    }

    @After
    fun tearDown() {
        try {
            onMain {
                try {
                    scope.cancel()
                    search?.reset()
                    if (::host.isInitialized) host.descendants.forEach { it.animate().setListener(null).cancel() }
                    if (::binding.isInitialized) binding.pdfView.recycle()
                } finally {
                    debugWasEnabled?.let { PDFView.enableDebugState(it) }
                    SearchTestHooks.reset()
                    ReaderTestHooks.reset()
                }
            }
        } finally {
            scenario?.close()
        }
    }

    @Test
    fun failedHighlightLeavesNoMarkerImmediatelyOrAfterDone() = checkFailedHighlight(dismiss = true)

    @Test
    fun failedHighlightLeavesNoMarkerWhenNextHitHighlightsNormally() = checkFailedHighlight(dismiss = false)

    private fun checkFailedHighlight(dismiss: Boolean) {
        val loaded = CountDownLatch(1)
        val failure = AtomicReference<Throwable>()
        onMain {
            binding.pdfView.fromBytes(searchPdf())
                .onLoad { loaded.countDown() }
                .onError { failure.set(it); loaded.countDown() }
                .load()
        }
        assertTrue("Synthetic PDF did not load", loaded.await(10, TimeUnit.SECONDS))
        failure.get()?.let { throw AssertionError("Synthetic PDF failed to load", it) }
        assertTrue(binding.pdfView.getPageRawText(2).contains("beacon"))
        onMain {
            assertEquals(2, binding.pdfView.pageCount)
            val doc = DocumentState(fileHash = "ReaderHookRegressionTest", length = 2)
            SearchSessionCache.put(doc.fileHash, "beacon", false, (1..2).map {
                SearchSessionCache.Hit(it, 0, it - 1, text = "beacon", inputEnd = 6)
            })
            search = SearchNavigationController(activity, binding, doc, preferences, { false },
                ReaderHistoryManager({ binding.pdfView }, {}), scope, {})
            search!!.startQuery("beacon", false)
        }
        await("Initial positive-control marker") { markerPages() == "0" }
        onMain {
            SearchTestHooks.failNextSearchHighlight()
            clickNext()
        }
        await("Failure label") {
            host.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)?.text?.toString() ==
                activity.getString(R.string.failed_to_highlight_search_result)
        }
        val immediatelyAfterFailure = onMain { markerPages() }
        var afterFailureDismissal: String? = null
        val positivePage = if (dismiss) {
            clickDone()
            afterFailureDismissal = onMain { markerPages() }
            onMain {
                val page = binding.pdfView.visiblePageIndex.toString()
                search!!.startQuery("beacon", false)
                page
            }
        } else {
            onMain { clickNext() }
            "0"
        }
        await("One-shot fault permits the next real highlight on page $positivePage") {
            markerPages().split(',').contains(positivePage) &&
                host.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)?.text?.toString() ==
                activity.getString(R.string.results)
        }
        val afterPositiveControl = onMain { markerPages() }
        clickDone()
        val afterPositiveDismissal = onMain { markerPages() }
        assertEquals("Failure must never leave a native marker page", "", immediatelyAfterFailure)
        afterFailureDismissal?.let { assertEquals("Done after failure must leave no marker", "", it) }
        assertEquals("Only the successful hit may have a marker", positivePage, afterPositiveControl)
        assertEquals("Successful highlights remain tracked for dismissal", "", afterPositiveDismissal)
    }

    @Test
    fun ltrHideCompletionPublishesZeroAndRetainsNegativePeak() = checkHideCompletion(View.LAYOUT_DIRECTION_LTR, -1f)

    @Test
    fun rtlHideCompletionPublishesZeroAndRetainsPositivePeak() = checkHideCompletion(View.LAYOUT_DIRECTION_RTL, 1f)

    private fun checkHideCompletion(direction: Int, sign: Float) {
        val shown = CountDownLatch(1)
        val hidden = CountDownLatch(1)
        val manager = onMain {
            binding.root.layoutDirection = direction
            val vm = ReaderViewModel(SavedStateHandle()).apply { isFullScreenToggled = true }
            FullScreenOptionsManager(binding, vm, preferences).also {
                binding.fullScreenButtonsLayout.animate().setListener(completion(shown))
                it.showAll()
            }
        }
        assertTrue("Show animation did not finish", shown.await(5, TimeUnit.SECONDS))
        onMain {
            assertEquals(View.VISIBLE, binding.fullScreenButtonsLayout.visibility)
            assertEquals(0f, binding.fullScreenButtonsLayout.translationX, 0f)
            binding.fullScreenButtonsLayout.animate().setListener(completion(hidden))
            manager.hideAll()
        }
        assertTrue("Hide animation did not finish", hidden.await(5, TimeUnit.SECONDS))
        onMain {
            assertFalse(manager.isVisible())
            assertEquals(View.GONE, binding.fullScreenButtonsLayout.visibility)
            assertEquals(1f, binding.fullScreenButtonsLayout.alpha, 0f)
            assertEquals(0f, binding.fullScreenButtonsLayout.translationX, 0f)
            val expectedPeak = sign * 16f * binding.root.resources.displayMetrics.density
            assertEquals(expectedPeak, ReaderTestHooks.fullScreenPanelHidePeakTranslationX, 0.01f)
            assertEquals("Live hook must match the reset panel", 0f, ReaderTestHooks.fullScreenPanelTranslationX, 0f)
        }
    }

    private fun completion(latch: CountDownLatch) = object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) { latch.countDown() }
    }

    private fun clickNext() {
        val next = host.descendants.single { it.contentDescription == activity.getString(R.string.next_search_result) }
        assertTrue(next.isEnabled)
        assertTrue(next.performClick())
    }

    private fun clickDone() {
        onMain { assertTrue(host.findViewById<View>(com.google.android.material.R.id.snackbar_action).performClick()) }
        await("Snackbar dismissal") { search?.isActive == false }
    }

    private fun markerPages(): String = binding.pdfView.debugState().split(';')
        .single { it.startsWith("markerPages=") }.substringAfter('=')

    private fun await(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (onMain(condition)) return
            SystemClock.sleep(10)
        }
        val state = onMain {
            if (::binding.isInitialized) {
                "markerPages=${markerPages()}, currentPage=${binding.pdfView.currentPage}, " +
                    "visiblePage=${binding.pdfView.visiblePageIndex}, " +
                    "snackbar=${host.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)?.text}"
            } else "reader not initialized"
        }
        throw AssertionError("Timed out: $description; $state")
    }

    private fun searchPdf(): ByteArray {
        val stream = "BT /F1 20 Tf 30 140 Td (beacon) Tj ET\n"
        val objects = listOf(
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R 4 0 R] /Count 2 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 200] /Resources << /Font << /F1 5 0 R >> >> /Contents 6 0 R >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 200] /Resources << /Font << /F1 5 0 R >> >> /Contents 6 0 R >>",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
            "<< /Length ${stream.length} >>\nstream\n${stream}endstream",
        )
        return buildString {
            append("%PDF-1.7\n")
            val offsets = objects.mapIndexed { index, obj ->
                val offset = length
                append("${index + 1} 0 obj\n$obj\nendobj\n")
                offset
            }
            val xref = length
            append("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
            offsets.forEach { append(String.format(Locale.ROOT, "%010d 00000 n \n", it)) }
            append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        }.toByteArray(Charsets.US_ASCII)
    }

    private fun <T> onMain(block: () -> T): T {
        val task = FutureTask { block() }
        instrumentation.runOnMainSync(task)
        return task.get()
    }
}
