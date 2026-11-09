// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.fixtures

import android.content.Intent
import android.net.Uri
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import java.io.File

object ControlledPickerDocument {

    fun writable(file: File, documentId: String = file.name): Uri {
        if (!file.isFile) {
            throw AssertionError("Cannot return ${file.absolutePath} from a picker because it is not a file")
        }
        val uri = StubSharerProvider.registerWritableDocumentPath(documentId, file.absolutePath)
        TestEnvironment.targetContext.grantUriPermission(
            TestEnvironment.targetPackage,
            uri,
            GRANT_FLAGS,
        )
        return uri
    }

    private const val GRANT_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
}
