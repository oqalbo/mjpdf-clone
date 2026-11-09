// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.baseline

import android.graphics.Bitmap
import java.io.File

object BitmapFiles {

    fun write(bitmap: Bitmap, target: File): File {
        target.parentFile?.mkdirs()
        target.outputStream().use { stream ->
            val compressed = bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            if (!compressed) {
                throw BaselineFailure("Could not encode a PNG into ${target.absolutePath}")
            }
        }
        return target
    }

    fun toArgb(bitmap: Bitmap): Bitmap =
        if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
}
