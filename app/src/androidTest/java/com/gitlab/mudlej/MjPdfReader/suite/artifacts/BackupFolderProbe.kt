// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.artifacts

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.gitlab.mudlej.MjPdfReader.data.BackupFolder
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment

data class BackupEntry(val name: String, val mimeType: String, val length: Long)

object BackupFolderProbe {

    fun grantedTreeUri(): Uri {
        val stored = TestEnvironment.defaultPreferences.getString(Preferences.backupFolderTreeUriKey, null)
            ?: throw AssertionError(
                "No backup folder tree URI is stored, so the export destination was never granted"
            )
        return Uri.parse(stored)
    }

    fun entries(): List<BackupEntry> {
        val tree = DocumentFile.fromTreeUri(TestEnvironment.targetContext, grantedTreeUri())
            ?: throw AssertionError("The granted backup tree cannot be opened as a document tree")
        val folder = tree.findFile(BackupFolder.folderName)
            ?: throw AssertionError(
                "The granted tree carries no ${BackupFolder.folderName} directory, so no export landed in it"
            )
        return folder.listFiles()
            .filter { it.isFile }
            .map { BackupEntry(it.name.orEmpty(), it.type.orEmpty(), it.length()) }
            .sortedBy { it.name }
    }
}
