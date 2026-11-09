// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data

import com.gitlab.mudlej.MjPdfReader.data.annotation.AnnotationJournal
import com.gitlab.mudlej.MjPdfReader.data.annotation.SourceKey
import com.gitlab.mudlej.MjPdfReader.data.signature.SignatureStore
import com.gitlab.mudlej.MjPdfReader.ui.home.CoverCache

class HistoryCleaner(
    private val pdfRepository: PdfRepository,
    private val annotationJournal: AnnotationJournal,
    private val signatureStore: SignatureStore,
    private val coverCache: CoverCache,
) {

    suspend fun clearReadingHistory(): Int {
        pdfRepository.removeAllAnnotationSaveDestinations()
        coverCache.clearAll()
        return pdfRepository.removeAllRecords()
    }

    suspend fun clearSavedPasswords(): Int {
        return pdfRepository.clearAllPasswords()
    }

    suspend fun clearBookmarks(): Int {
        return pdfRepository.removeAllUserBookmarks()
    }

    fun clearAnnotationJournalsAndSignature() {
        annotationJournal.deleteAll()
        signatureStore.delete()
    }

    suspend fun deleteDocument(fileHash: String) {
        val record = pdfRepository.findRecord(fileHash)
        pdfRepository.removeUserBookmarksByFileHash(fileHash)
        pdfRepository.removeAnnotationSaveDestinationByLastSavedHash(fileHash)
        if (record != null) {
            annotationJournal.delete(record.uri)
            pdfRepository.removeAnnotationSaveDestinationBySourceKey(SourceKey.of(record.uri))
            pdfRepository.removeRecords(listOf(fileHash))
        }
        coverCache.invalidate(fileHash)
    }

    suspend fun deleteDocuments(fileHashes: List<String>) {
        fileHashes.forEach { deleteDocument(it) }
    }
}
