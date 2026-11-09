// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.security

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.actions.MetadataDialog
import com.gitlab.mudlej.MjPdfReader.suite.bookmarks.TableOfContentsRobot
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.FileDescriptors
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.state.DocumentLoadState
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.tableofcontents.TableOfContentsActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class HostileResourceTest {
    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()
    private val contents = TableOfContentsRobot()

    @Test
    @TestId("SEC-11")
    fun aCyclicAndOverDeepOutlineIsBoundedAtSixtyFourLevels() {
        ActivityScenario.launch<MainActivity>(
            SecurityReader.fixtureViewIntent(SecurityFixtures.CYCLIC_OUTLINE)
        ).use {
            reader.awaitReady()

            SecurityReader.openAction(R.string.table_of_contents)
            contents.awaitShown(CONTENTS_TIMEOUT_MS)
            contents.awaitLoaded(CONTENTS_TIMEOUT_MS)
            assertTrue(
                "the contents list must be displayed rather than left on an empty message",
                contents.rowCount() > 0,
            )

            contents.expandAll()
            val rows = Await.untilValue(
                "the expanded contents list settles at a finite row count",
                Await.VERY_LONG_TIMEOUT_MS,
                pollMs = POLL_MS,
                read = { contents.rowCount() },
            ) { it > 0 }
            assertTrue(
                "a 500 level chain clipped at $MAX_DEPTH must not produce more rows than the fixture wrote, " +
                    "the list showed $rows",
                rows in 1..DECLARED_OUTLINE_ENTRIES,
            )

            val sampled = (0 until minOf(rows, SAMPLE_ROWS)).map { position ->
                contents.scrollTo(position)
                contents.rowTitle(position).orEmpty() to contents.rowIndentMarginStart(position)
            }
            val titles = sampled.map { it.first }
            assertEquals(
                "the visited set in recursiveGetBookmark must break the sibling cycle, so no entry may repeat. " +
                    "The sampled titles were $titles",
                titles.size,
                titles.toSet().size,
            )
            val indents = sampled.map { it.second }.toSet()
            assertTrue(
                "recursiveGetBookmark enters at depth 0 and returns as soon as depth exceeds " +
                    "MAX_BOOKMARK_DEPTH, so $MAX_DEPTH is the deepest depth value it may emit and " +
                    "${MAX_DEPTH + 1} is the most distinct indentation levels that can appear. It showed " +
                    "${indents.size}: $indents",
                indents.size <= MAX_DEPTH + 1,
            )

            contents.pressBack()
            Screens.awaitGone(TableOfContentsActivity::class.java, Await.LONG_TIMEOUT_MS)
        }
    }

    @Test
    @TestId("SEC-12")
    fun aNonGoToOutlineActionDoesNothing() {
        val traffic = NetworkWatch.sample()

        ActivityScenario.launch<MainActivity>(
            SecurityReader.fixtureViewIntent(SecurityFixtures.CYCLIC_OUTLINE_ACTIONS)
        ).use {
            reader.awaitReady()
            val startingPage = reader.readState().page

            for (position in ACTION_ROW_POSITIONS) {
                SecurityReader.openAction(R.string.table_of_contents)
                contents.awaitShown(CONTENTS_TIMEOUT_MS)
                contents.awaitLoaded(CONTENTS_TIMEOUT_MS)
                contents.scrollTo(position)
                contents.tapRow(position)
                TestEnvironment.uiDevice.waitForIdle()

                SecurityReader.holdForegroundPackage(TestEnvironment.targetPackage)

                if (Screens.isInTask(TableOfContentsActivity::class.java)) {
                    contents.pressBack()
                    Screens.awaitGone(TableOfContentsActivity::class.java, Await.LONG_TIMEOUT_MS)
                }
                SecurityReader.awaitForegroundPackage(TestEnvironment.targetPackage)
            }

            assertEquals(
                "nativeGetBookmarkDestIndex returns -1 for any action other than PDFACTION_GOTO, so neither " +
                    "tap may move the reader",
                startingPage,
                reader.readState().page,
            )
        }

        NetworkWatch.assertUnchangedSince(traffic, "tapping a Launch and a URI outline entry")
    }

    @Test
    @TestId("SEC-13")
    fun anInvalidEmbeddedFontDoesNotCrashTheRenderer() {
        for (name in FONT_VARIANTS) {
            val fixture = SecurityFixtures.file(name)
            val digests = mutableListOf<String>()

            repeat(DETERMINISM_RUNS) {
                ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
                    reader.awaitShown()
                    val state = reader.awaitLoadSettled(Await.LONG_TIMEOUT_MS)
                    assertEquals(
                        "$name must open, otherwise the FreeType path under test is never reached",
                        DocumentLoadState.LOADED,
                        state.loadState,
                    )
                    reader.awaitFullQualityRender()
                    digests += PageCapture.pdfViewDigest()

                    SecurityReader.openAction(R.string.file_metadata)
                    MetadataDialog.awaitShown(Await.LONG_TIMEOUT_MS)
                    val pages = MetadataDialog.pageCountText()
                    assertTrue(
                        "File Properties must display for $name, its page count row read $pages",
                        !pages.isNullOrBlank(),
                    )
                    TestEnvironment.uiDevice.pressBack()
                    TestEnvironment.uiDevice.waitForIdle()
                }
                Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
                FileDescriptors.awaitNoneFor(fixture)
            }

            assertEquals(
                "a broken font must fall back to the same pixels every time, $name produced $digests",
                1,
                digests.toSet().size,
            )
        }
    }

    @Test
    @TestId("SEC-14")
    fun aMalformedEmbeddedImageRendersADeterministicRegion() {
        for (name in IMAGE_VARIANTS) {
            val fixture = SecurityFixtures.file(name)
            val digests = mutableListOf<String>()

            repeat(DETERMINISM_RUNS) {
                ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
                    reader.awaitShown()
                    val state = reader.awaitLoadSettled(Await.LONG_TIMEOUT_MS)
                    assertEquals(
                        "$name must open, otherwise the image decode path under test is never reached",
                        DocumentLoadState.LOADED,
                        state.loadState,
                    )
                    reader.awaitFullQualityRender()
                    digests += PageCapture.pdfViewDigest()
                }
                Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
                FileDescriptors.awaitNoneFor(fixture)
            }

            assertEquals(
                "a broken image must leave the same hole every time rather than drawing whatever was in the " +
                    "buffer, $name produced $digests",
                1,
                digests.toSet().size,
            )
        }
    }

    private companion object {
        const val CONTENTS_TIMEOUT_MS = 5_000L
        const val POLL_MS = 250L
        const val MAX_DEPTH = SecurityFixtures.MAX_BOOKMARK_DEPTH
        const val DECLARED_OUTLINE_ENTRIES = 503
        const val SAMPLE_ROWS = 120
        const val DETERMINISM_RUNS = 2

        val ACTION_ROW_POSITIONS = listOf(0, 1)

        val FONT_VARIANTS = listOf(
            SecurityFixtures.BAD_FONT,
            SecurityFixtures.BAD_FONT_LENGTH1,
        )

        val IMAGE_VARIANTS = listOf(
            SecurityFixtures.BAD_IMAGE,
            SecurityFixtures.BAD_IMAGE_BPC,
        )
    }
}
