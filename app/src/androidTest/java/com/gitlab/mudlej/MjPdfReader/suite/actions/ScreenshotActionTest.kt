// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.actions
import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.os.Build
import android.net.Uri
import com.github.barteksc.pdfviewer.PDFView
import com.gitlab.mudlej.MjPdfReader.core.debug.ReaderTestHooks
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import java.util.Calendar
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.actions.ConfigurableAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class ScreenshotActionTest {

    private val suite = SuiteRules.standard()
    private val cleanup = ActionsCleanupRule.forFixtures(ActionsFixtures.NORMAL)

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(suite).around(cleanup)

    private val act = ActionsRobot()

    @Test
    @TestId("ACT-43")
    fun theScreenshotLandsInTheGalleryAsARealJpegMatchingThePageView() {

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(ActionsFixtures.uri(ActionsFixtures.NORMAL))).use {
            act.awaitReady()

            act.enterFullscreen()
            act.revealOverlayColumn()
            FullScreenOverlay.awaitColumnVisible()

            val viewport = act.reader().readState()
            val expectedWidth = viewport.viewWidth
            val expectedHeight = viewport.viewHeight

            FullScreenOverlay.tap(ActionLabels.of(ConfigurableAction.SCREENSHOT))

            Snackbars.awaitText(ActionLabels.string(R.string.screenshot_saved))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val row = ScreenshotOracle.awaitSingleRow(prefix())
                assertTrue(
                    "the file name must carry the document name and a timestamp, it reads ${row.displayName}",
                    NAME_PATTERN.matches(row.displayName),
                )
                assertTrue(
                    "the row must live under the app's gallery folder, it reads ${row.relativePath}",
                    row.relativePath.orEmpty().startsWith(ScreenshotOracle.expectedRelativePathPrefix()),
                )

                val size = ScreenshotOracle.sizeOf(row)
                assertEquals("the capture width must match the page view", expectedWidth, size.width as Int?)
                assertEquals("the capture height must match the page view", expectedHeight, size.height as Int?)
                assertTrue("the stored bytes must begin with the JPEG marker", ScreenshotOracle.isJpeg(row))
            } else {
                val file = ScreenshotOracle.awaitSinglePublicPicture(prefix())
                assertTrue(
                    "the file name must carry the document name and a timestamp, it reads ${file.name}",
                    NAME_PATTERN.matches(file.name),
                )
                val size = ScreenshotOracle.sizeOf(file)
                assertEquals("the capture width must match the page view", expectedWidth, size.width as Int?)
                assertEquals("the capture height must match the page view", expectedHeight, size.height as Int?)
                assertTrue("the stored bytes must begin with the JPEG marker", ScreenshotOracle.isJpeg(file))
            }

            assertTrue(
                "a gallery screenshot must not leave an unused private duplicate",
                ScreenshotOracle.awaitExternalFilesCopyCount(prefix(), 0).isEmpty(),
            )
        }
    }

    @Test
    @TestId("ACT-45")
    fun twoCapturesInTheSameSecondProduceTwoRowsRatherThanOverwritingEachOther() {
        ActionsPreferences.seedPrimaryAction(ConfigurableAction.SCREENSHOT)

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(ActionsFixtures.uri(ActionsFixtures.NORMAL))).use {
            act.awaitReady()

            val viewport = act.reader().readState()
            val expectedWidth = viewport.viewWidth
            val expectedHeight = viewport.viewHeight

            val frozen = Calendar.getInstance().apply {
                set(2026, Calendar.JANUARY, 2, 12, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            ReaderTestHooks.screenshotTimeMillis = frozen
            try {
                ToolbarItems.awaitLabel(ToolbarItems.primaryId, ActionLabels.of(ConfigurableAction.SCREENSHOT))
                ToolbarItems.tapPrimary()
                val firstUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ScreenshotOracle.awaitSingleRow(prefix()).uri
                } else {
                    Uri.fromFile(ScreenshotOracle.awaitSinglePublicPicture(prefix()))
                }
                val firstBytes = screenshotBytes(firstUri)
                TestEnvironment.onMainThread {
                    val activity = checkNotNull(TestEnvironment.resumedActivityOfType(MainActivity::class.java))
                    activity.findViewById<PDFView>(R.id.pdfView).jumpTo(1, false)
                }
                act.reader().awaitPageIndex(1)
                act.reader().awaitFullQualityRender()
                ToolbarItems.tapPrimary()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val rows = ScreenshotOracle.awaitRowCount(prefix(), 2, Await.LONG_TIMEOUT_MS)
                    assertArrayEquals("the second capture must not replace the first image", firstBytes, screenshotBytes(firstUri))
                    assertEquals("the two captures must be distinct rows", 2, rows.map { it.id }.distinct().size)
                    assertEquals(
                        "the two captures must carry distinct names, so neither overwrote the other",
                        2,
                        rows.map { it.displayName }.distinct().size,
                    )
                    assertTrue("both captures must use the same frozen second: ${rows.map { it.displayName }}", rows.all { it.displayName.startsWith(prefix() + " - " + FROZEN_STAMP) })
                    val secondUri = rows.single { it.uri != firstUri }.uri
                    assertFalse("the two pages must produce different images", firstBytes.contentEquals(screenshotBytes(secondUri)))
                    rows.forEach { row ->
                        val size = ScreenshotOracle.sizeOf(row)
                        assertEquals("capture ${row.displayName} width", expectedWidth, size.width as Int?)
                        assertEquals("capture ${row.displayName} height", expectedHeight, size.height as Int?)
                    }
                } else {
                    val files = ScreenshotOracle.awaitPublicPictureCount(prefix(), 2, Await.LONG_TIMEOUT_MS)
                    assertArrayEquals("the second capture must not replace the first image", firstBytes, screenshotBytes(firstUri))
                    assertEquals(
                        "the two legacy captures must be distinct files",
                        2,
                        files.map { it.canonicalPath }.distinct().size,
                    )
                    assertEquals(
                        "the two legacy captures must carry distinct names, so neither overwrote the other",
                        2,
                        files.map { it.name }.distinct().size,
                    )
                    assertTrue("both legacy captures must use the same frozen second: ${files.map { it.name }}", files.all { it.name.startsWith(prefix() + " - " + FROZEN_STAMP) })
                    val secondUri = Uri.fromFile(files.single { Uri.fromFile(it) != firstUri })
                    assertFalse("the two pages must produce different images", firstBytes.contentEquals(screenshotBytes(secondUri)))
                    files.forEach { file ->
                        val size = ScreenshotOracle.sizeOf(file)
                        assertEquals("capture ${file.name} width", expectedWidth, size.width as Int?)
                        assertEquals("capture ${file.name} height", expectedHeight, size.height as Int?)
                    }
                }
            } finally {
                ReaderTestHooks.screenshotTimeMillis = null
            }
        }
    }

    private fun screenshotBytes(uri: Uri): ByteArray =
        checkNotNull(TestEnvironment.targetContext.contentResolver.openInputStream(uri)).use { it.readBytes() }

    private fun prefix(): String = ActionsFixtures.screenshotPrefix(ActionsFixtures.NORMAL)

    private companion object {
        const val FROZEN_STAMP = "2026_01_02-12_00_00"
        val NAME_PATTERN = Regex("""^.+ - \d{4}_\d{2}_\d{2}-\d{2}_\d{2}_\d{2}\.jpg$""")
    }
}
