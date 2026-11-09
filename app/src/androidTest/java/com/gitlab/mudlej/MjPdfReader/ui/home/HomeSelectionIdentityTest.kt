// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home

import android.content.res.Resources
import android.net.Uri
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.annotation.UiThreadTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.entity.ScannedPdfEntry
import com.gitlab.mudlej.MjPdfReader.data.entity.ScannedPdfMetadataState
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityHomeBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeSelectionIdentityTest {
    private lateinit var binding: ActivityHomeBinding
    private lateinit var selection: HomeSelectionController
    private var items = emptyList<HomeItem>()
    private var actionItems = emptyList<HomeItem>()
    private val entry = ScannedPdfEntry("/books/book.pdf", 100, 1_000, null)

    @Before @UiThreadTest fun setUp() {
        val context = ContextThemeWrapper(
            InstrumentationRegistry.getInstrumentation().targetContext, R.style.HomeTheme,
        )
        val activity = object : AppCompatActivity() {
            override fun getResources(): Resources = context.resources
        }
        binding = ActivityHomeBinding.inflate(LayoutInflater.from(context))
        selection = HomeSelectionController(
            activity, binding, currentItems = { items }, currentContext = { SelectionContext.LIBRARY },
            onSelectionChanged = {}, onStatusBatch = { actionItems = it }, onRemoveRecentBatch = {},
            onHideBatch = {}, onDeleteBatch = {},
        )
    }

    @Test @UiThreadTest fun metadataArrivalPreservesSelectionAndActionsResolveCurrentContent() {
        val provisional = HomeItem.fromScan(entry)
        items = listOf(provisional)
        selection.begin(provisional)
        val detailed = HomeItem.fromScan(entry.copy(
            hash = "content-hash", pageCount = 17, metadataState = ScannedPdfMetadataState.COMPLETE,
        ))
        items = listOf(detailed)

        assertNotEquals(provisional.hash, detailed.hash)
        assertEquals(provisional.stableKey, detailed.stableKey)
        assertEquals(setOf(detailed.stableKey), selection.selectedKeys)
        binding.selectionToolbar.menu.performIdentifierAction(R.id.statusBatchOption, 0)
        assertEquals(listOf(detailed), actionItems)
        selection.toggle(detailed)
        assertTrue(selection.selectedKeys.isEmpty())
        assertFalse(selection.active)
    }

    @Test @UiThreadTest fun identicalContentAtDifferentUrisIsSelectedIndependently() {
        val first = HomeItem.fromScan(entry.copy(hash = "shared"))
        val second = HomeItem.fromScan(entry.copy(path = "/books/copy.pdf", hash = "shared"))
        items = listOf(first, second)

        selection.begin(first)
        binding.selectionToolbar.menu.performIdentifierAction(R.id.statusBatchOption, 0)
        assertEquals(listOf(first), actionItems)
        selection.toggle(second)
        selection.toggle(first)
        assertEquals(setOf(second.stableKey), selection.selectedKeys)
        binding.selectionToolbar.menu.performIdentifierAction(R.id.statusBatchOption, 0)
        assertEquals(listOf(second), actionItems)
    }

    @Test @UiThreadTest fun selectedKeysAreSnapshotsAndUseNormalizedUriIdentity() {
        val item = HomeItem.fromScan(entry).copy(uri = Uri.parse("FILE:///books/book.pdf"))
        selection.begin(item)
        val snapshot = selection.selectedKeys
        selection.toggle(item.copy(uri = Uri.parse("file:///books/book.pdf"), hash = "new-hash"))

        assertEquals(setOf("file:///books/book.pdf"), snapshot)
        assertTrue(selection.selectedKeys.isEmpty())
        assertFalse(selection.active)
    }

    @Test @UiThreadTest fun actionsDoNotSubstituteADifferentUriWithTheSameHash() {
        val selected = HomeItem.fromScan(entry.copy(hash = "shared"))
        selection.begin(selected)
        items = listOf(HomeItem.fromScan(entry.copy(path = "/books/copy.pdf", hash = "shared")))

        binding.selectionToolbar.menu.performIdentifierAction(R.id.statusBatchOption, 0)

        assertTrue(actionItems.isEmpty())
        selection.finish()
        assertTrue(selection.selectedKeys.isEmpty())
    }
}
