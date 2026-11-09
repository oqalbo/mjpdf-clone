// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.support

import android.content.Context
import android.content.ContextWrapper
import java.io.File

class FakeFilesContext(filesDir: File, cacheDir: File) : ContextWrapper(null) {
    private val files = filesDir
    private val cache = cacheDir
    override fun getFilesDir(): File = files
    override fun getCacheDir(): File = cache
    override fun getNoBackupFilesDir(): File = File(files, "no_backup").apply { mkdirs() }
    override fun getDir(name: String, mode: Int): File = File(files, "app_$name").apply { mkdirs() }
    override fun getApplicationContext(): Context = this
}
