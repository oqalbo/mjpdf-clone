// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.pdf

import org.junit.Assert.*
import org.junit.Test

class PageThumbnailBucketTest {
    @Test fun widthsRoundUpToTheNextBucketAndClampAtBothEnds() {
        for ((width, bucket) in listOf(-1 to 120, 0 to 120, 120 to 120, 121 to 180,
            180 to 180, 181 to 240, 241 to 320, 321 to 440, 441 to 600, 600 to 600, 601 to 600)) {
            assertEquals("width $width", bucket, PageThumbnailCache.bucketFor(width))
        }
    }
}
