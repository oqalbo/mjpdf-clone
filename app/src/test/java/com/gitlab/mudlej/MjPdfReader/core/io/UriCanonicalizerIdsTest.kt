// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.core.io

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import org.junit.Ignore

class UriCanonicalizerIdsTest {
    private val primary = File("/primary")

    @Test fun primaryVolumeUsesTheSuppliedRootIgnoringCase() {
        assertEquals(File(primary, "Download/a.pdf"), UriCanonicalizer.pathForExternalStorageDocId("primary:Download/a.pdf", primary))
        assertEquals(primary, UriCanonicalizer.pathForExternalStorageDocId("PRIMARY:", primary))
    }

    @Test fun removableVolumesUseStorageAndMalformedIdsAreRejected() {
        assertEquals(File("/storage/1234-5678/Books/x"), UriCanonicalizer.pathForExternalStorageDocId("1234-5678:Books/x", primary))
        assertNull(UriCanonicalizer.pathForExternalStorageDocId("primary", primary))
    }

    @Test fun downloadsPrefixesResolveToNumericIds() {
        for (id in listOf("msf:42", "msd:42", "42")) {
            assertEquals(id, 42L, UriCanonicalizer.downloadsDocumentId(id))
        }
        for (id in listOf("abc", "", "msf:", "9223372036854775808", "raw:/storage/emulated/0/Download/a.pdf")) {
            assertNull(id, UriCanonicalizer.downloadsDocumentId(id))
        }
    }

    @Ignore("Pending ruling: home maps under /storage/home, expected the primary Documents folder")
    @Test fun homeDocumentIdsResolveUnderPrimaryDocuments() {
        assertEquals(File(primary, "Documents/Books/a.pdf"), UriCanonicalizer.pathForExternalStorageDocId("home:Books/a.pdf", primary))
    }
}
