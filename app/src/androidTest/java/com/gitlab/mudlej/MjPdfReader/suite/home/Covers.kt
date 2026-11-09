// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.home

import android.graphics.Bitmap
import android.graphics.Canvas
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import java.io.File
import java.io.FileOutputStream

object Covers {

    val buckets: List<Int> = listOf(160, 320, 640)

    fun directory(): File = File(TestEnvironment.targetContext.cacheDir, "covers")

    fun bucketFor(widthPx: Int): Int = buckets.firstOrNull { it >= widthPx } ?: buckets.last()

    fun gridBucket(spanCount: Int): Int =
        bucketFor(TestEnvironment.targetContext.resources.displayMetrics.widthPixels / spanCount)

    fun fileFor(hash: String, bucket: Int): File = File(directory(), "$hash-w$bucket.jpg")

    fun filesFor(hash: String): List<File> =
        directory().listFiles { file -> file.name.startsWith("$hash-w") }?.toList().orEmpty()

    fun temporaryFiles(): List<File> =
        directory().listFiles { file -> file.name.endsWith(".tmp") }?.toList().orEmpty()

    fun names(): List<String> = directory().listFiles()?.map { it.name }.orEmpty()

    fun plantSentinel(hash: String, bucket: Int, color: Int): File {
        val target = fileFor(hash, bucket)
        target.parentFile?.mkdirs()
        val bitmap = Bitmap.createBitmap(bucket, bucket, Bitmap.Config.RGB_565)
        Canvas(bitmap).drawColor(color)
        FileOutputStream(target).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        }
        bitmap.recycle()
        return target
    }
}
