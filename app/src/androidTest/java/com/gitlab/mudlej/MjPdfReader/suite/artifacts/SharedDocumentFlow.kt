// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.artifacts

import android.net.Uri
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.StubSharerProvider
import java.io.File

object SharedDocumentFlow {

    fun requireProviderInstalled() {
        val installed = TestEnvironment.targetContext.packageManager
            .resolveContentProvider(StubSharerProvider.AUTHORITY, 0) != null
        if (!installed) {
            throw AssertionError(
                "The stub content provider on authority ${StubSharerProvider.AUTHORITY} is not installed. It is " +
                    "declared in app/src/debug/AndroidManifest.xml, and without it the durable copy path cannot " +
                    "be reached at all, because every real provider on this image is either excluded by " +
                    "TemporaryCopyImporter or grants a persistable permission"
            )
        }
    }

    fun serve(name: String, file: File): Uri {
        requireProviderInstalled()
        return StubSharerProvider.registerPath(name, file.absolutePath)
    }

    fun serveUnder(name: String, reportedDisplayName: String, file: File): Uri {
        val uri = serve(name, file)
        StubSharerProvider.setDisplayName(name, reportedDisplayName)
        return uri
    }

    fun clear() {
        runCatching { StubSharerProvider.clear() }
    }
}
