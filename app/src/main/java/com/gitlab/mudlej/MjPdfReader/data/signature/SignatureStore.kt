// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data.signature

import android.content.Context
import java.io.File

class SignatureStore(context: Context) {

    private val file = File(File(context.filesDir, DIRECTORY), FILE_NAME)

    fun load(): SignatureData? {
        if (!file.exists()) {
            return null
        }
        return runCatching { SignatureData.fromJson(file.readText()) }.getOrNull()
    }

    fun save(data: SignatureData) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(data.toJson())
        }
    }

    fun delete() {
        runCatching { file.delete() }
    }

    companion object {
        private const val DIRECTORY = "signature"
        private const val FILE_NAME = "signature.json"
    }
}
