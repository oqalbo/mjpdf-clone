// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.load

import android.content.Context
import android.net.Uri
import android.util.Log
import com.gitlab.mudlej.MjPdfReader.BuildConfig
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.io.DocumentSize
import com.gitlab.mudlej.MjPdfReader.core.io.DurableCopyStore
import com.gitlab.mudlej.MjPdfReader.core.io.PersistedGrantKeeper
import com.gitlab.mudlej.MjPdfReader.core.io.UriCanonicalizer
import com.gitlab.mudlej.MjPdfReader.core.io.forLog
import com.gitlab.mudlej.MjPdfReader.data.HistoryPolicy
import com.gitlab.mudlej.MjPdfReader.data.PdfRepository
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.data.SharedCopyMode
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class TemporaryCopyImporter(
    private val context: Context,
    private val pdfRepository: PdfRepository,
    private val historyPolicy: HistoryPolicy,
    private val pref: Preferences,
    private val currentDocument: () -> Pair<Uri?, String>,
    private val scope: CoroutineScope,
    private val onCopySaved: (String, Int) -> Unit,
    private val onCopyNeedsConsent: (CopyConsentRequest) -> Unit,
) : DocumentListener {

    override fun onRecordAvailable(fileHash: String) {
        if (!historyPolicy.canRecord()) {
            return
        }
        val (uri, name) = currentDocument()
        if (uri == null || !isTemporaryCopyEligible(uri.scheme, uri.authority, BuildConfig.APPLICATION_ID)) {
            return
        }
        synchronized(inFlightHashes) {
            if (!inFlightHashes.add(fileHash)) {
                return
            }
        }
        scope.launch {
            try {
                importIfTemporary(fileHash, uri, name)
            } catch (throwable: Throwable) {
                Log.e(TAG, "importIfTemporary failed for ${uri.forLog()}", throwable)
            } finally {
                synchronized(inFlightHashes) { inFlightHashes.remove(fileHash) }
            }
        }
    }

    private suspend fun importIfTemporary(fileHash: String, uri: Uri, name: String) {
        if (PersistedGrantKeeper.takeReadGrant(context, uri)) {
            return
        }
        if (UriCanonicalizer.canonicalize(context, uri) != null) {
            return
        }
        val record = pdfRepository.findRecord(fileHash) ?: return
        val scannedFile = pdfRepository.findScannedPdfsByHash(fileHash)
            .map { File(it.path) }
            .firstOrNull { it.canRead() }
        if (scannedFile != null) {
            pdfRepository.updateRecordIdentity(fileHash, Uri.fromFile(scannedFile), record.fileName, record.lastOpened)
            pdfRepository.setRecordSourceUri(fileHash, uri.toString())
            return
        }
        if (record.uri != uri && isReadable(record.uri)) {
            return
        }
        val sizeBytes = DocumentSize.of(context, uri)
        val freeBytes = DocumentSize.availableBytes(context)
        if (pref.getSharedCopyMode() == SharedCopyMode.ASK || temporaryCopyNeedsConsent(sizeBytes, freeBytes)) {
            val fits = temporaryCopyFits(sizeBytes, freeBytes)
            onCopyNeedsConsent(CopyConsentRequest(fileHash, uri, name, sizeBytes, fits))
            return
        }
        performCopy(fileHash, uri, name)
    }

    suspend fun performCopy(fileHash: String, uri: Uri, name: String) {
        val record = pdfRepository.findRecord(fileHash) ?: return
        val copy = DurableCopyStore.saveCopy(context, uri, name, fileHash) ?: return
        pdfRepository.updateRecordIdentity(fileHash, copy.uri, record.fileName, record.lastOpened)
        pdfRepository.setRecordSourceUri(fileHash, uri.toString())
        onCopySaved(fileHash, if (copy.isPublic) R.string.shared_copy_saved_public else R.string.shared_copy_saved_private)
    }

    private fun isReadable(uri: Uri): Boolean {
        return if (uri.scheme == "file") {
            uri.path?.let { File(it).canRead() } == true
        } else {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { } != null
            }.getOrDefault(false)
        }
    }

    private companion object {
        const val TAG = "TemporaryCopyImporter"
        val inFlightHashes: MutableSet<String> = mutableSetOf()

    }
}

internal fun temporaryCopyNeedsConsent(sizeBytes: Long?, freeBytes: Long?): Boolean {
    if (sizeBytes == null) {
        return false
    }
    if (sizeBytes > LARGE_FILE_BYTES) {
        return true
    }
    return freeBytes != null && freeBytes - sizeBytes < FREE_SPACE_FLOOR_BYTES
}

internal fun temporaryCopyFits(sizeBytes: Long?, freeBytes: Long?): Boolean =
    sizeBytes == null || freeBytes == null || freeBytes - sizeBytes >= FREE_SPACE_FLOOR_BYTES

internal fun isTemporaryCopyEligible(scheme: String?, authority: String?, applicationId: String): Boolean =
    scheme == "content" && authority != null && authority !in EXCLUDED_AUTHORITIES &&
        !authority.startsWith(applicationId)

private const val LARGE_FILE_BYTES = 250L * 1024 * 1024
private const val FREE_SPACE_FLOOR_BYTES = 500L * 1024 * 1024
private val EXCLUDED_AUTHORITIES = setOf(
    "media",
    "com.android.externalstorage.documents",
    "com.android.providers.downloads.documents",
    "com.android.providers.media.documents",
)
