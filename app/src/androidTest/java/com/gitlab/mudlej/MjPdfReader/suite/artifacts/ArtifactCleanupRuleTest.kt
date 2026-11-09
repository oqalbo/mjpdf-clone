// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.artifacts

import android.os.Environment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class ArtifactCleanupRuleTest : ArtifactTestCase() {

    @Test
    @TestId("ART-42-ISOLATION")
    fun claimingAScreenshotPrefixClearsOldCapturesAndPreservesOtherFiles() {
        val name = "art42-isolation.pdf"
        val document = copyFixtureToDownloads(ArtifactFixtures.SMALL, name)
        val prefix = ScreenshotFlow.namePrefixFor(name)
        cleanup.ownImagesNamedLike(prefix)
        ScreenshotFlow.seedAsPrimaryToolbarAction()

        openReader(document).use {
            ScreenshotFlow.take()
            ScreenshotFlow.awaitSaved()
            val oldCapture = MediaStoreProbe.awaitImagesNamedLike(prefix, 1).single()
            val bytes = MediaStoreProbe.bytesOf(oldCapture)
            val control = File(Environment.getExternalStorageDirectory(),
                "${MediaStoreProbe.screenshotPicturesPath}art42-isolation-control.jpg")
            cleanup.own(control)
            Fixtures.pushBytes(bytes, control)
            Fixtures.scanAndWait(control)

            val nextOwner = ArtifactCleanupRule()
            nextOwner.ownImagesNamedLike(prefix)
            assertEquals("a fresh owner must start without old captures", emptyList<MediaRow>(), MediaStoreProbe.imagesNamedLike(prefix))
            assertArrayEquals("claiming one prefix must preserve other pictures", bytes, control.readBytes())

            ScreenshotFlow.take()
            ScreenshotFlow.awaitSaved()
            MediaStoreProbe.awaitImagesNamedLike(prefix, 1)
            nextOwner.ownImagesNamedLike(prefix)
            assertEquals("registering the same owner again must preserve its new capture", 1, MediaStoreProbe.imagesNamedLike(prefix).size)
        }
    }
}
