// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.usernotes

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.debug.DebugRootDescription
import com.gitlab.mudlej.MjPdfReader.core.io.appDateTimeFormatter
import com.gitlab.mudlej.MjPdfReader.core.io.convertDateString
import com.gitlab.mudlej.MjPdfReader.core.ui.AppSnackbar
import com.gitlab.mudlej.MjPdfReader.core.ui.attachFilterSearchView
import com.gitlab.mudlej.MjPdfReader.core.ui.configureSearchIcon
import com.gitlab.mudlej.MjPdfReader.core.ui.setupScreenChrome
import com.gitlab.mudlej.MjPdfReader.core.ui.applyIncognitoNightModeFromIntent
import com.gitlab.mudlej.MjPdfReader.core.ui.applyIncognitoOverlayFromIntent
import com.gitlab.mudlej.MjPdfReader.core.ui.tintIconsForChrome
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityUserNotesBinding
import com.gitlab.mudlej.MjPdfReader.pdf.ExtractorScreen
import com.gitlab.mudlej.MjPdfReader.pdf.PDF
import com.gitlab.mudlej.MjPdfReader.pdf.PdfExtractor
import com.gitlab.mudlej.MjPdfReader.pdf.SweptHighlight
import com.gitlab.mudlej.MjPdfReader.pdf.sweepPageHighlights
import com.gitlab.mudlej.MjPdfReader.ui.tableofcontents.TableOfContentsPathResolver
import com.google.android.material.snackbar.Snackbar
import com.shockwave.pdfium.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.time.LocalDateTime

class UserNotesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserNotesBinding
    private lateinit var pdfExtractor: PdfExtractor
    private val extractorScreen = ExtractorScreen(this)
    private val noteAdapter = UserNoteAdapter(::onNoteClicked, notesMode = true)
    private var notes: List<SweptHighlight> = listOf()
    private var query: String = ""
    private var sortByDate: Boolean = false
    private var documentMeta: PdfDocument.Meta? = null
    private var actionBarMenu: Menu? = null
    private val scannedPageLiveData = MutableLiveData<Int>()

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { destinationUri ->
        destinationUri?.let(::exportNotes)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyIncognitoNightModeFromIntent()
        super.onCreate(savedInstanceState)
        applyIncognitoOverlayFromIntent()
        binding = ActivityUserNotesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        DebugRootDescription.attachLayoutDirection(binding.root)
        setupScreenChrome()
        title = getString(R.string.user_notes_title)

        binding.userNotesRecyclerView.apply {
            adapter = noteAdapter
            layoutManager = LinearLayoutManager(this@UserNotesActivity)
        }
        binding.progressBar.visibility = View.VISIBLE

        extractorScreen.open(getString(R.string.file_opening_error)) { extractor ->
            pdfExtractor = extractor
            documentMeta = withContext(Dispatchers.IO) { extractor.getMeta() }
            loadTableOfContentsPaths()
            scanNotes()
        }
    }

    override fun onDestroy() {
        extractorScreen.close()
        super.onDestroy()
    }

    private fun loadTableOfContentsPaths() {
        lifecycleScope.launch {
            val resolver = TableOfContentsPathResolver.load(
                this@UserNotesActivity,
                intent.getStringExtra(PDF.filePathKey),
                intent.getStringExtra(PDF.passwordKey),
            )
            if (resolver !== TableOfContentsPathResolver.EMPTY) {
                noteAdapter.tableOfContentsPathResolver = resolver
            }
        }
    }

    private fun scanNotes() {
        val pageCount = pdfExtractor.getPageCount()
        binding.progressBar.visibility = View.GONE
        binding.scanProgressBar.max = pageCount
        binding.scanProgressBar.progress = 0
        binding.scanProgressBar.visibility = View.VISIBLE
        scannedPageLiveData.observe(this) { pageNumber ->
            binding.scanProgressBar.progress = pageNumber
        }

        lifecycleScope.launch(Dispatchers.Default) {
            val collected = mutableListOf<SweptHighlight>()
            var lastSubmittedCount = 0

            for (pageIndex in 0 until pageCount) {
                yield()
                collected.addAll(sweepPageHighlights(pdfExtractor, pageIndex).filter { it.note.isNotBlank() })
                scannedPageLiveData.postValue(pageIndex + 1)

                val isBatchBoundary = pageIndex % SCAN_BATCH_PAGES == 0 || pageIndex == pageCount - 1
                if (isBatchBoundary && collected.size > lastSubmittedCount) {
                    lastSubmittedCount = collected.size
                    val snapshot = collected.toList()
                    withContext(Dispatchers.Main) {
                        notes = snapshot
                        binding.message.visibility = View.GONE
                        refreshDisplayedNotes()
                        refreshMenu()
                    }
                }
            }

            withContext(Dispatchers.Main) {
                notes = collected.toList()
                binding.scanProgressBar.visibility = View.GONE
                refreshDisplayedNotes()
                refreshMenu()
                binding.message.text = getString(R.string.no_user_notes)
                binding.message.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun refreshDisplayedNotes(): Int {
        var displayed = filterAnnotations(notes, query, null)
        if (sortByDate) {
            displayed = displayed.sortedWith(annotationComparator)
        }
        noteAdapter.submitList(displayed)
        return displayed.size
    }

    private fun refreshMenu() {
        val menu = actionBarMenu ?: return
        configureSearchIcon(menu, notes.isNotEmpty())
        menu.findItem(R.id.export_list)?.isVisible = notes.isNotEmpty()
        menu.findItem(R.id.sort_by_creation_date)?.let { sortItem ->
            sortItem.isVisible = notes.any { it.creationDate != null }
            sortItem.isChecked = sortByDate
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.annotation_list_menu, menu)
        menu.tintIconsForChrome(this)
        actionBarMenu = menu
        refreshMenu()
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.attachFilterSearchView(
            binding.root,
            onQueryChanged = { newQuery ->
                query = newQuery
                refreshDisplayedNotes()
            },
            onClosed = {
                query = ""
                refreshDisplayedNotes()
                true
            },
        )
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            R.id.sort_by_creation_date -> {
                sortByDate = !sortByDate
                item.isChecked = sortByDate
                refreshDisplayedNotes()
            }
            R.id.export_list -> exportLauncher.launch(exportFileName())
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun onNoteClicked(item: SweptHighlight) {
        val resultIntent = Intent()
        resultIntent.putExtra(PDF.chosenTableOfContentsEntryKey, item.pageIndex)
        resultIntent.putExtra(PDF.chosenHighlightGroupKey, item.groupKey)
        resultIntent.putExtra(PDF.chosenHighlightAnnotationIndexKey, item.annotationIndex)
        item.bounds?.let { resultIntent.putExtra(PDF.chosenHighlightBoundsKey, it) }
        setResult(PDF.TABLE_OF_CONTENTS_RESULT_OK, resultIntent)
        finish()
    }

    private fun documentTitle(): String {
        val metaTitle = documentMeta?.title?.takeIf { it.isNotBlank() }
        val fileName = intent.getStringExtra(PDF.nameKey)?.takeIf { it.isNotBlank() }
        return metaTitle ?: fileName ?: getString(R.string.user_notes_title)
    }

    private fun exportFileName(): String {
        return "${documentTitle()} - ${getString(R.string.user_notes_title)}.txt"
    }

    private fun buildExportText(): String {
        return buildAnnotationExport(
            documentTitle(),
            documentMeta?.author,
            notes,
            noteAdapter.tableOfContentsPathResolver::resolve,
            AnnotationExportLabels(
                author = { getString(R.string.export_by_author, it) },
                summary = { getString(R.string.export_notes_line, it, LocalDateTime.now().format(appDateTimeFormatter)) },
                page = { getString(R.string.bookmark_page_label, it) },
                date = ::convertDateString,
                quote = getString(R.string.export_quote_label),
                note = getString(R.string.note),
            ),
            sortByDate = sortByDate,
            includeBlankNotes = true,
        )
    }

    private fun exportNotes(destinationUri: Uri) {
        lifecycleScope.launch {
            val text = buildExportText()
            val written = withContext(Dispatchers.IO) {
                runCatching {
                    val stream = contentResolver.openOutputStream(destinationUri, "wt")
                        ?: contentResolver.openOutputStream(destinationUri, "w")
                        ?: return@runCatching false
                    stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                    true
                }.getOrDefault(false)
            }
            val messageRes = if (written) R.string.export_done else R.string.export_failed
            AppSnackbar.make(binding.root, getString(messageRes), Snackbar.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val SCAN_BATCH_PAGES = 50
    }
}
