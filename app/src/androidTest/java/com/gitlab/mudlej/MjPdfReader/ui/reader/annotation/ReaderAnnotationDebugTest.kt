// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.annotation

import android.graphics.Color
import android.graphics.RectF
import android.net.Uri
import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.barteksc.pdfviewer.PDFView
import com.gitlab.mudlej.MjPdfReader.ui.reader.PostSaveAction
import com.gitlab.mudlej.MjPdfReader.ui.reader.ReaderUi
import com.gitlab.mudlej.MjPdfReader.ui.reader.ReaderViewModel
import com.shockwave.pdfium.PdfDocument
import org.json.JSONArray
import org.json.JSONObject
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
class ReaderAnnotationDebugTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val views = mutableListOf<PDFView>()
    private var debugWasEnabled = false

    @Before
    fun enableDebug() = onMain {
        debugWasEnabled = PDFView.isDebugStateEnabled()
        PDFView.enableDebugState(true)
    }

    @After
    fun recycleReaders() = onMain {
        try {
            views.forEach { it.recycle() }
        } finally {
            PDFView.enableDebugState(debugWasEnabled)
        }
    }

    @Test
    fun savingUpdatesOnlyTheSpecifiedReaderBeforeAndAfterTheSecondIsRecycled() {
        val a = newReader()
        onMain { a.debug.setSavingAnnotations(true) }
        val b = newReader()

        onMain {
            assertTrue(a.vm.isSavingAnnotations)
            assertFalse(b.vm.isSavingAnnotations)
            assertEquals(listOf(true), a.ui.savingUpdates)
            assertTrue(b.ui.savingUpdates.isEmpty())

            a.debug.setSavingAnnotations(false)
            assertFalse(a.vm.isSavingAnnotations)
            assertFalse(b.vm.isSavingAnnotations)
            assertEquals(listOf(true, false), a.ui.savingUpdates)
            assertTrue(b.ui.savingUpdates.isEmpty())

            b.debug.setSavingAnnotations(true)
            assertFalse(a.vm.isSavingAnnotations)
            assertTrue(b.vm.isSavingAnnotations)
            assertEquals(listOf(true, false), a.ui.savingUpdates)
            assertEquals(listOf(true), b.ui.savingUpdates)

            b.view.recycle()
            a.debug.setSavingAnnotations(true)
            a.debug.setSavingAnnotations(false)
            assertFalse(a.vm.isSavingAnnotations)
            assertTrue(b.vm.isSavingAnnotations)
            assertEquals(listOf(true, false, true, false), a.ui.savingUpdates)
            assertEquals(listOf(true), b.ui.savingUpdates)
        }
    }

    @Test
    fun nativeDumpsKeepTheirShapeAndReaderAfterTheSecondIsRecycled() {
        val a = loadReader("A")
        assertNativeDumps(a.debug, "A")
        val b = loadReader("B")

        assertNativeDumps(a.debug, "A")
        assertNativeDumps(b.debug, "B")
        onMain { b.view.recycle() }

        assertEquals("[]", b.debug.dumpPageHighlights(0))
        assertEquals("{}", b.debug.dumpFormField(0, 3))
        assertNativeDumps(a.debug, "A")
    }

    @Test
    fun pendingStampsKeepTheirShapeAndRemainIndependentWhenTheFirstIsRecycled() {
        val a = loadReader("A")
        onMain {
            assertEquals("{}", a.debug.dumpPendingStamp())
            a.view.startStampPlacement(
                0, RectF(10f, 90f, 70f, 30f),
                arrayOf(floatArrayOf(0f, 0f, 0.25f, 0.25f, 0.75f, 0.75f, 1f, 1f)),
                Color.RED, 0.125f,
            )
        }
        val b = loadReader("B")
        onMain {
            assertEquals("{}", b.debug.dumpPendingStamp())
            b.view.startStampPlacement(
                0, RectF(20f, 120f, 100f, 80f),
                arrayOf(floatArrayOf(0f, 0f, 0.25f, 0.25f, 0.75f, 0.75f, 1f, 1f)),
                Color.BLUE, 0.25f,
            )
        }

        assertStamp(a.debug, "[10,90,70,30]", Color.RED, 0.125)
        assertStamp(b.debug, "[20,120,100,80]", Color.BLUE, 0.25)
        onMain {
            a.view.recycle()
            assertEquals("{}", a.debug.dumpPendingStamp())
        }
        assertStamp(b.debug, "[20,120,100,80]", Color.BLUE, 0.25)
        onMain {
            b.view.cancelStampPlacement()
            assertEquals("{}", b.debug.dumpPendingStamp())
        }
    }

    @Test
    fun absentNativeDataAndDisabledViewerDebuggingKeepEmptyDumpShapes() {
        val reader = newReader()
        assertEquals("[]", reader.debug.dumpPageHighlights(0))
        assertEquals("{}", reader.debug.dumpFormField(0, 0))
        assertEquals("{}", onMain { reader.debug.dumpPendingStamp() })

        val loaded = loadReader("A")
        assertNativeDumps(loaded.debug, "A")
        assertEquals("[]", loaded.debug.dumpPageHighlights(-1))
        assertEquals("[]", loaded.debug.dumpPageHighlights(1))
        assertEquals("{}", loaded.debug.dumpFormField(0, 99))
        assertEquals("{}", loaded.debug.dumpFormField(1, 3))

        onMain { PDFView.enableDebugState(false) }
        assertEquals("[]", loaded.debug.dumpPageHighlights(0))
        assertEquals("{}", loaded.debug.dumpFormField(0, 3))
        onMain { PDFView.enableDebugState(true) }
        assertNativeDumps(loaded.debug, "A")
    }

    private fun assertNativeDumps(debug: ReaderAnnotationDebug, label: String) {
        val groups = JSONArray(debug.dumpPageHighlights(0))
        assertEquals(2, groups.length())
        val first = groups.getJSONObject(0)
        assertEquals(setOf("group", "color", "quote", "note", "index", "rects"), first.keys().asSequence().toSet())
        assertEquals("$label-first", first.getString("group"))
        assertEquals(Color.RED, first.getInt("color"))
        assertEquals("quote \"$label\"", first.getString("quote"))
        assertEquals("line one\nline two", first.getString("note"))
        assertEquals(0, first.getInt("index"))
        assertEquals(2, first.getInt("rects"))
        val second = groups.getJSONObject(1)
        assertEquals(first.keys().asSequence().toSet(), second.keys().asSequence().toSet())
        assertEquals("$label-second", second.getString("group"))
        assertEquals(Color.BLUE, second.getInt("color"))
        assertEquals("second", second.getString("quote"))
        assertEquals("", second.getString("note"))
        assertEquals(1, second.getInt("index"))
        assertEquals(1, second.getInt("rects"))

        val field = JSONObject(debug.dumpFormField(0, 3))
        assertEquals(setOf("page", "index", "type", "name", "value", "checked", "readOnly"), field.keys().asSequence().toSet())
        assertEquals(0, field.getInt("page"))
        assertEquals(3, field.getInt("index"))
        assertEquals(PdfDocument.FormField.TYPE_TEXT_FIELD, field.getInt("type"))
        assertEquals("$label-field", field.getString("name"))
        assertEquals("value $label", field.getString("value"))
        assertEquals(false, field.get("checked"))
        assertEquals(true, field.get("readOnly"))
    }

    private fun assertStamp(debug: ReaderAnnotationDebug, expectedRect: String, color: Int, strokeWidth: Double) {
        val stamp = JSONObject(onMain { debug.dumpPendingStamp() })
        assertEquals(setOf("page", "rect", "color", "strokeWidth"), stamp.keys().asSequence().toSet())
        assertEquals(0, stamp.getInt("page"))
        val rect = stamp.getJSONArray("rect")
        assertEquals(4, rect.length())
        val expected = JSONArray(expectedRect)
        repeat(4) { assertEquals(expected.getDouble(it), rect.getDouble(it), 0.0) }
        assertEquals(color, stamp.getInt("color"))
        assertEquals(strokeWidth, stamp.getDouble("strokeWidth"), 0.0)
    }

    private fun newReader(): Reader = onMain {
        val view = PDFView(instrumentation.targetContext, null)
        views.add(view)
        val vm = ReaderViewModel(SavedStateHandle())
        val ui = RecordingReaderUi(vm)
        Reader(view, vm, ui, ReaderAnnotationDebug(view, vm, ui))
    }

    private fun loadReader(label: String): Reader {
        val reader = newReader()
        val bytes = annotatedPdf(label)
        val loaded = CountDownLatch(1)
        val failure = AtomicReference<Throwable>()
        onMain {
            reader.view.layout(0, 0, 300, 200)
            reader.view.fromBytes(bytes)
                .onLoad { loaded.countDown() }
                .onError { failure.set(it); loaded.countDown() }
                .load()
        }
        assertTrue("Reader $label did not load", loaded.await(10, TimeUnit.SECONDS))
        failure.get()?.let { throw AssertionError("Reader $label failed to load", it) }
        assertEquals(1, onMain { reader.view.pageCount })
        return reader
    }

    private fun annotatedPdf(label: String): ByteArray {
        val objects = listOf(
            "<< /Type /Catalog /Pages 2 0 R /AcroForm 8 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 200] /Resources << >> /Annots [4 0 R 5 0 R 6 0 R 7 0 R] >>",
            "<< /Type /Annot /Subtype /Highlight /Rect [10 150 60 170] /QuadPoints [10 170 60 170 10 150 60 150] /C [1 0 0] /MJGroup ($label-first) /MJQuote (quote \"$label\") /Contents (line one\\nline two) >>",
            "<< /Type /Annot /Subtype /Highlight /Rect [70 150 120 170] /QuadPoints [70 170 120 170 70 150 120 150] /C [0 0 1] /MJGroup ($label-second) /MJQuote (second) /Contents () >>",
            "<< /Type /Annot /Subtype /Highlight /Rect [10 120 60 140] /QuadPoints [10 140 60 140 10 120 60 120] /C [0 1 0] /MJGroup ($label-first) /MJQuote (later) /Contents (later) >>",
            "<< /Type /Annot /Subtype /Widget /FT /Tx /T ($label-field) /V (value $label) /Ff 1 /Rect [150 30 250 60] /P 3 0 R /DA (/Helv 12 Tf 0 g) >>",
            "<< /Fields [7 0 R] /DR << /Font << /Helv 9 0 R >> >> /DA (/Helv 12 Tf 0 g) >>",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
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

    private data class Reader(
        val view: PDFView,
        val vm: ReaderViewModel,
        val ui: RecordingReaderUi,
        val debug: ReaderAnnotationDebug,
    )

    private class RecordingReaderUi(private val vm: ReaderViewModel) : ReaderUi {
        val savingUpdates = mutableListOf<Boolean>()

        override fun updateDirtyUi() {
            assertEquals(Looper.getMainLooper(), Looper.myLooper())
            savingUpdates.add(vm.isSavingAnnotations)
        }

        override fun updateTitle() = Unit
        override fun updateActionBar() = Unit
        override fun updateDirtyUiPosition() = Unit
        override fun showProgress() = Unit
        override fun hideProgress() = Unit
        override fun checkHasFile() = true
        override fun runAfterDirtyAnnotationPrompt(action: PostSaveAction, uri: Uri?) = Unit
    }
}
