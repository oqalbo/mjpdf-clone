// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.history

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.ui.confirmDialog
import com.gitlab.mudlej.MjPdfReader.core.ui.setupScreenChrome
import com.gitlab.mudlej.MjPdfReader.data.AppDatabase
import com.gitlab.mudlej.MjPdfReader.data.HistoryCleaner
import com.gitlab.mudlej.MjPdfReader.data.PdfRepository
import com.gitlab.mudlej.MjPdfReader.data.annotation.AnnotationJournal
import com.gitlab.mudlej.MjPdfReader.data.entity.PdfRecord
import com.gitlab.mudlej.MjPdfReader.data.signature.SignatureStore
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityReadingHistoryBinding
import com.gitlab.mudlej.MjPdfReader.ui.home.CoverCache
import java.time.LocalDateTime
import kotlinx.coroutines.launch

class ReadingHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReadingHistoryBinding
    private val pdfRepository by lazy { PdfRepository(AppDatabase.getInstance(applicationContext)) }
    private val historyCleaner by lazy {
        HistoryCleaner(
            pdfRepository,
            AnnotationJournal(applicationContext),
            SignatureStore(applicationContext),
            CoverCache.getInstance(applicationContext),
        )
    }
    private val historyAdapter = ReadingHistoryAdapter(::confirmDeleteRecord)
    private var records: List<PdfRecord> = listOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReadingHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupScreenChrome()
        title = getString(R.string.reading_history)

        binding.readingHistoryRecyclerView.apply {
            adapter = historyAdapter
            layoutManager = LinearLayoutManager(this@ReadingHistoryActivity)
        }
        loadRecords()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun loadRecords() {
        lifecycleScope.launch {
            val unsetDate = LocalDateTime.parse(PdfRecord.UNSET_DATE)
            val sorted = pdfRepository.findAllRecords().sortedWith(
                compareBy<PdfRecord> { it.lastOpened == unsetDate }.thenByDescending { it.lastOpened }
            )
            showRecords(sorted)
        }
    }

    private fun showRecords(loadedRecords: List<PdfRecord>) {
        records = loadedRecords
        historyAdapter.submitList(records)
        binding.message.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun confirmDeleteRecord(record: PdfRecord) {
        confirmDialog(
            this,
            R.string.delete_dialog_title,
            getString(R.string.reading_history_delete_message, ReadingHistoryAdapter.displayNameOf(record)),
            R.string.delete,
        ) { deleteRecord(record) }
    }

    private fun deleteRecord(record: PdfRecord) {
        lifecycleScope.launch {
            historyCleaner.deleteDocument(record.hash)
            showRecords(records.filterNot { it.hash == record.hash })
        }
    }
}
