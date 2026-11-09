// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.intake

import android.content.Intent
import android.net.Uri
import com.gitlab.mudlej.MjPdfReader.pdf.PDF
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.StubSharerProvider
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import java.io.File

object StubSharer {

    const val AUTHORITY = StubSharerProvider.AUTHORITY

    fun isInstalled(): Boolean =
        TestEnvironment.targetContext.packageManager.resolveContentProvider(AUTHORITY, 0) != null

    fun requireInstalled() {
        if (isInstalled()) return
        throw AssertionError(
            "The stub content provider on authority $AUTHORITY is not installed. It is declared in " +
                "app/src/debug/AndroidManifest.xml and implemented in the debug source set, so a build " +
                "that lacks it cannot serve any shared copy test in this file."
        )
    }

    fun publishAsset(name: String, assetName: String): Uri {
        val bytes = TestEnvironment.testContext.assets.open(assetName).use { it.readBytes() }
        return publish(name, bytes)
    }

    fun publish(name: String, bytes: ByteArray): Uri = StubSharerProvider.publish(name, bytes)

    fun register(name: String, file: File): Uri {
        if (!file.isFile) {
            throw AssertionError("Cannot serve ${file.absolutePath} through $AUTHORITY, it is not a file")
        }
        return StubSharerProvider.registerPath(name, file.absolutePath)
    }

    fun clear() {
        runCatching { StubSharerProvider.clear() }
    }

    fun sharedViewIntent(uri: Uri): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, PDF.FILE_TYPE)
            setClass(TestEnvironment.targetContext, MainActivity::class.java)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    fun ungrantedViewIntent(uri: Uri): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, PDF.FILE_TYPE)
            setClass(TestEnvironment.targetContext, MainActivity::class.java)
        }
}
