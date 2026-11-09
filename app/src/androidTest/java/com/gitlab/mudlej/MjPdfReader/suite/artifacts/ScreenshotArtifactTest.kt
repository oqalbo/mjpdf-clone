// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.artifacts

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class ScreenshotArtifactTest : ArtifactTestCase() {

    @Test
    @TestId("ART-42")
    fun theScreenshotMediaStoreRowCarriesTheRightNamePathAndType() {
        val document = copyFixtureToDownloads(ArtifactFixtures.SMALL, SHOT_NAME)
        val prefix = ScreenshotFlow.namePrefixFor(SHOT_NAME)
        cleanup.ownImagesNamedLike(prefix)
        ScreenshotFlow.seedAsPrimaryToolbarAction()

        openReader(document).use {
            ScreenshotFlow.take()
            ScreenshotFlow.awaitSaved()
            val row = MediaStoreProbe.awaitImagesNamedLike(prefix, 1).single()
            val bytes = MediaStoreProbe.bytesOf(row)

            assertTrue(
                "the gallery name must carry the document name and a timestamp, it reads ${row.displayName}",
                ScreenshotFlow.namePattern.matches(row.displayName),
            )
            assertTrue(
                "the gallery name must be built from the document name, it reads ${row.displayName}",
                row.displayName.startsWith(prefix),
            )
            assertEquals(
                "the screenshot must use the public Pictures path for this Android storage branch",
                MediaStoreProbe.screenshotPicturesPath,
                row.relativePath,
            )
            assertTrue("the row must report a non zero size", row.size > 0L)
            assertTrue(
                "the stored bytes must open with the JPEG magic number",
                bytes.size >= ArtifactBytes.JPEG_MAGIC.size &&
                    bytes.copyOf(ArtifactBytes.JPEG_MAGIC.size).contentEquals(ArtifactBytes.JPEG_MAGIC),
            )
            assertEquals(
                "the row must declare the type of the bytes it holds. ScreenshotController writes the pattern " +
                    "image/* into MediaStore.MediaColumns.MIME_TYPE, and a pattern is not a type, so a gallery " +
                    "reading this column learns nothing about the file",
                JPEG_MIME_TYPE,
                row.mimeType,
            )
        }
    }

    @Test
    @TestId("ART-43")
    fun screenshotDimensionsEqualTheReaderCanvasAndFollowARotation() {
        val document = copyFixtureToDownloads(ArtifactFixtures.SMALL, ROTATION_NAME)
        val prefix = ScreenshotFlow.namePrefixFor(ROTATION_NAME)
        cleanup.ownImagesNamedLike(prefix)
        ScreenshotFlow.seedAsPrimaryToolbarAction()

        openReader(document).use {
            val portrait = reader.canvasSize()
            ScreenshotFlow.take()
            ScreenshotFlow.awaitSaved()
            val portraitRow = MediaStoreProbe.awaitImagesNamedLike(prefix, 1).single()
            val portraitBounds = ScreenshotFlow.boundsOf(MediaStoreProbe.bytesOf(portraitRow))

            assertEquals("the capture width must equal the page view's width", portrait.first, portraitBounds.width)
            assertEquals("the capture height must equal the page view's height", portrait.second, portraitBounds.height)
            assertEquals("the capture must be decoded as a JPEG", JPEG_MIME_TYPE, portraitBounds.mimeType)

            reader.awaitLandscapeCanvas()
            val landscape = reader.canvasSize()
            assertNotEquals(
                "the rotation must really have changed the canvas before a second capture proves anything",
                portrait,
                landscape,
            )

            ScreenshotFlow.take()
            val rows = MediaStoreProbe.awaitImagesNamedLike(prefix, 2)
            val landscapeRow = rows.firstOrNull { it.uri != portraitRow.uri }
                ?: throw AssertionError("The second capture produced no new row, the rows read $rows")
            val landscapeBounds = ScreenshotFlow.boundsOf(MediaStoreProbe.bytesOf(landscapeRow))

            assertEquals(
                "the landscape capture width must equal the reconfigured page view's width",
                landscape.first,
                landscapeBounds.width,
            )
            assertEquals(
                "the landscape capture height must equal the reconfigured page view's height",
                landscape.second,
                landscapeBounds.height,
            )
        }
    }

    @Test
    @TestId("ART-44")
    @TestDuration(DurationCategory.NORMAL)
    fun theScreenshotDepictsThePageTheReaderWasShowing() {
        val document = copyFixtureToDownloads(ArtifactFixtures.SMALL, DEPICTS_NAME)
        val prefix = ScreenshotFlow.namePrefixFor(DEPICTS_NAME)
        cleanup.ownImagesNamedLike(prefix)
        ScreenshotFlow.seedAsPrimaryToolbarAction()

        openReader(document).use {
            reader.swipeToPage(THIRD_PAGE_INDEX)
            ScreenshotFlow.take()
            ScreenshotFlow.awaitSaved()
            val thirdRow = MediaStoreProbe.awaitImagesNamedLike(prefix, 1).single()
            val thirdPage = ScreenshotFlow.decode(MediaStoreProbe.bytesOf(thirdRow))

            assertTrue(
                "a capture taken before the page finished drawing is a flat rectangle, so the image must " +
                    "carry more than one colour",
                ScreenshotFlow.distinctColourCount(thirdPage) > 1,
            )

            reader.swipeToPage(FOURTH_PAGE_INDEX)
            ScreenshotFlow.take()
            val rows = MediaStoreProbe.awaitImagesNamedLike(prefix, 2)
            val fourthRow = rows.firstOrNull { it.uri != thirdRow.uri }
                ?: throw AssertionError("The second capture produced no new row, the rows read $rows")
            val fourthPage = ScreenshotFlow.decode(MediaStoreProbe.bytesOf(fourthRow))

            assertTrue(
                "the page four capture must carry more than one colour too",
                ScreenshotFlow.distinctColourCount(fourthPage) > 1,
            )
            assertNotEquals(
                "a capture of page four must not be the same image as a capture of page three, otherwise the " +
                    "screenshot is a stale frame rather than what the reader was showing",
                ArtifactBytes.sha256(MediaStoreProbe.bytesOf(thirdRow)),
                ArtifactBytes.sha256(MediaStoreProbe.bytesOf(fourthRow)),
            )
        }
    }

    @Test
    @TestId("ART-45")
    fun aScreenshotTakenInNightModeIsDarkerThanTheSamePageInDayMode() {
        val document = copyFixtureToDownloads(ArtifactFixtures.SMALL, NIGHT_NAME)
        val prefix = ScreenshotFlow.namePrefixFor(NIGHT_NAME)
        cleanup.ownImagesNamedLike(prefix)
        ScreenshotFlow.seedAsPrimaryToolbarAction()

        openReader(document).use {
            ScreenshotFlow.take()
            ScreenshotFlow.awaitSaved()
        }
        leaveReader()
        val dayRow = MediaStoreProbe.awaitImagesNamedLike(prefix, 1).single()
        val dayLuminance = ScreenshotFlow.meanLuminance(ScreenshotFlow.decode(MediaStoreProbe.bytesOf(dayRow)))

        SettingsSeeder.seed(Preferences.pdfPagesThemeKey, DARK_THEME)
        openReader(document).use {
            ScreenshotFlow.take()
            val rows = MediaStoreProbe.awaitImagesNamedLike(prefix, 2)
            val nightRow = rows.firstOrNull { it.uri != dayRow.uri }
                ?: throw AssertionError("The night mode capture produced no new row, the rows read $rows")
            val nightLuminance = ScreenshotFlow.meanLuminance(
                ScreenshotFlow.decode(MediaStoreProbe.bytesOf(nightRow))
            )

            assertTrue(
                "the night mode capture must be darker than the day mode capture of the same page, so the " +
                    "screenshot reflects the appearance settings the reader was drawing with. Day mode read " +
                    "$dayLuminance and night mode read $nightLuminance",
                nightLuminance < dayLuminance,
            )
        }
    }

    @Test
    @TestId("ART-46")
    fun twoScreenshotsProduceTwoGalleryEntriesAndNoPrivateLeftovers() {
        val document = copyFixtureToDownloads(ArtifactFixtures.SMALL, TWICE_NAME)
        val prefix = ScreenshotFlow.namePrefixFor(TWICE_NAME)
        cleanup.ownImagesNamedLike(prefix)
        ScreenshotFlow.seedAsPrimaryToolbarAction()

        openReader(document).use {
            ScreenshotFlow.take()
            ScreenshotFlow.awaitSaved()
            ScreenshotFlow.take()
            val rows = MediaStoreProbe.awaitImagesNamedLike(prefix, 2)

            assertEquals("the two captures must be two distinct rows", 2, rows.map { it.uri }.distinct().size)
            rows.forEach { row ->
                val bytes = MediaStoreProbe.bytesOf(row)
                assertTrue("the row ${row.displayName} must hold bytes", bytes.isNotEmpty())
                assertTrue(
                    "the row ${row.displayName} must hold a JPEG",
                    bytes.copyOf(ArtifactBytes.JPEG_MAGIC.size).contentEquals(ArtifactBytes.JPEG_MAGIC),
                )
            }
            assertEquals(
                "the private staging copy must be removed once the gallery insert succeeded, otherwise every " +
                    "screenshot is kept twice for the life of the install. The private pictures directory holds " +
                    AppDirectories.namesIn(AppDirectories.privatePictures),
                emptyList<String>(),
                AppDirectories.namesIn(AppDirectories.privatePictures),
            )
        }
    }

    private companion object {
        const val SHOT_NAME = "art42-screenshot.pdf"
        const val ROTATION_NAME = "art43-rotation.pdf"
        const val DEPICTS_NAME = "art44-depicts.pdf"
        const val NIGHT_NAME = "art45-night.pdf"
        const val TWICE_NAME = "art46-twice.pdf"

        const val JPEG_MIME_TYPE = "image/jpeg"
        const val DARK_THEME = "dark"

        const val THIRD_PAGE_INDEX = 2
        const val FOURTH_PAGE_INDEX = 3
    }
}
