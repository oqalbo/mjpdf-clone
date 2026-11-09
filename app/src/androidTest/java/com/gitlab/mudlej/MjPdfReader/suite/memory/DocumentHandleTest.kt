// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.memory

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.bookmarks.LinksRobot
import com.gitlab.mudlej.MjPdfReader.suite.bookmarks.TableOfContentsRobot
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.FileDescriptors
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.intake.IntakeDb
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.links.LinksActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.tableofcontents.TableOfContentsActivity
import com.gitlab.mudlej.MjPdfReader.ui.userhighlights.UserHighlightsActivity
import com.gitlab.mudlej.MjPdfReader.ui.usernotes.UserNotesActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class DocumentHandleTest {
    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()

    @Test
    @TestId("MEM-01")
    @TestDuration(DurationCategory.LONG)
    fun fiftyDifferentDocumentsOpenAndCloseWithEveryHandleReturned() {
        val fixtures = MemoryFixtures.fiftyDistinct()

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixtures.first())).use {
            reader.awaitReady()
        }
        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        MemoryReader.awaitDescriptorCount(fixtures.first(), 0)

        val totalBaseline = MemoryReader.awaitTotalDescriptorsStable()
        val baseline = FileDescriptors.countsFor(fixtures)
        assertEquals(
            "none of the fifty fixture paths may already be open before the measured cycles",
            emptyMap<File, Int>(),
            baseline.filterValues { it != 0 },
        )

        for (fixture in fixtures) {
            ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
                reader.awaitReady()
            }
            Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        }

        val settled = FileDescriptors.countsFor(fixtures)
        val stillOpen = settled.filterValues { it != 0 }
        assertEquals(
            "every document must give its descriptor back to its warmed path-specific baseline, these did " +
                "not: ${stillOpen.mapKeys { it.key.name }}",
            emptyMap<File, Int>(),
            stillOpen,
        )
        val totalSettled = MemoryReader.awaitTotalDescriptorsStable()
        assertTrue(
            "the process descriptor total may drift by fewer than $DESCRIPTOR_ALLOWANCE entries across 50 " +
                "open and close cycles, it went from $totalBaseline to $totalSettled. This aggregate includes " +
                "unrelated process descriptors and remains an owner-policy signal alongside the exact path check",
            totalSettled - totalBaseline < DESCRIPTOR_ALLOWANCE,
        )

        val records = IntakeDb.awaitRecordCount(fixtures.size, Await.LONG_TIMEOUT_MS)
        assertEquals(
            "fifty distinct documents must produce fifty distinct identities, otherwise some open was served " +
                "from another document's record and the descriptor assertion proved nothing",
            fixtures.size,
            records.map { it.hash }.toSet().size,
        )
    }

    @Test
    @TestId("MEM-02")
    fun everyExtractorScreenReleasesItsDocumentOnLeaving() {
        for (screen in EXTRACTOR_SCREENS) {
            val fixture = MemoryFixtures.file(screen.fixture)

            ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
                reader.awaitReady()
                val readerOnly = FileDescriptors.countFor(fixture)
                assertTrue(
                    "the reader must hold at least one descriptor on ${fixture.name} before the screen opens, " +
                        "otherwise the return assertion is vacuous",
                    readerOnly >= 1,
                )

                MemoryReader.openAction(screen.labelRes)
                Screens.awaitResumed(screen.activity, Await.LONG_TIMEOUT_MS)
                screen.awaitContent()

                TestEnvironment.uiDevice.pressBack()
                Screens.awaitGone(screen.activity, Await.LONG_TIMEOUT_MS)

                MemoryReader.awaitDescriptorCount(fixture, readerOnly)
                MemoryReader.holdDescriptorCount(fixture, readerOnly)
            }
            Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
            MemoryReader.awaitDescriptorCount(fixture, 0)
        }
    }

    @Test
    @TestId("MEM-07")
    fun readingDirectionDetectionReleasesItsDocument() {
        val fixture = MemoryFixtures.file(MemoryFixtures.ARABIC)
        SettingsSeeder.seed(Preferences.horizontalScrollKey, true)

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
            val state = reader.awaitReady()
            assertEquals(
                "the fixture must be the Arabic document, otherwise detection had nothing to detect",
                MemoryFixtures.ARABIC_PAGES as Int?,
                state.pageCount,
            )
            assertEquals(
                "reading-direction detection is intentionally disabled outside horizontal reading, so the " +
                    "fixture must have opened with a horizontal layout before its result can be used as evidence",
                false,
                state.swipeVertical,
            )

            MemoryReader.awaitDescriptorCount(fixture, 1)
            MemoryReader.holdDescriptorCount(fixture, 1, MemoryReader.DESCRIPTOR_STABLE_LONG_MS)

            val settled = reader.awaitState(
                "the horizontally laid out Arabic fixture is detected as right to left",
                Await.LONG_TIMEOUT_MS,
            ) { it.readingDirectionRtl == true }
            assertEquals(
                "detection must actually have run and reported right to left, not been skipped",
                true,
                settled.readingDirectionRtl,
            )
            MemoryReader.awaitDescriptorCount(fixture, 1)
            MemoryReader.holdDescriptorCount(fixture, 1, MemoryReader.DESCRIPTOR_STABLE_LONG_MS)
        }

        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        MemoryReader.awaitDescriptorCount(fixture, 0)
    }

    @Test
    @TestId("MEM-09")
    fun theReaderHoldsExactlyOneDocumentHandleForTheOpenPdf() {
        val fixture = MemoryFixtures.file(MemoryFixtures.NORMAL)

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
            reader.awaitReady()
            val samples = mutableListOf<Pair<String, Int>>()
            samples += "after load" to FileDescriptors.countFor(fixture)

            setZoom(ZOOMED_IN)
            reader.awaitRenderIdle(Await.LONG_TIMEOUT_MS)
            samples += "zoomed in" to FileDescriptors.countFor(fixture)

            setZoom(ZOOMED_OUT)
            reader.awaitRenderIdle(Await.LONG_TIMEOUT_MS)
            samples += "zoomed out" to FileDescriptors.countFor(fixture)

            MemoryReader.jumpToPage(reader, DEEP_PAGE_INDEX)
            reader.awaitRenderIdle(Await.LONG_TIMEOUT_MS)
            samples += "after a page jump" to FileDescriptors.countFor(fixture)

            MemoryReader.openAction(R.string.switch_to_dark_mode)
            reader.awaitRenderIdle(Await.LONG_TIMEOUT_MS)
            samples += "after switching theme" to FileDescriptors.countFor(fixture)

            val wrong = samples.filter { it.second != 1 }
            assertEquals(
                "reconfiguring the view must reuse the open document rather than opening a second copy. " +
                    "These samples read something other than one: $wrong",
                emptyList<Pair<String, Int>>(),
                wrong,
            )

            MemoryReader.openAction(R.string.crop_margins)
            reader.awaitLoaded(Await.VERY_LONG_TIMEOUT_MS)
            reader.awaitRenderIdle(Await.VERY_LONG_TIMEOUT_MS)
            MemoryReader.awaitDescriptorCount(fixture, 1)
            MemoryReader.holdDescriptorCount(fixture, 1)
        }

        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        MemoryReader.awaitDescriptorCount(fixture, 0)
    }

    private fun setZoom(zoom: Float) {
        TestEnvironment.onMainThread {
            val view = MemoryReader.pdfView()
            view.debugSetViewport(zoom, view.currentXOffset, view.currentYOffset)
        }
    }

    private data class ExtractorScreen(
        val labelRes: Int,
        val fixture: String,
        val activity: Class<out android.app.Activity>,
        val awaitContent: () -> Unit,
    )

    private companion object {
        const val DESCRIPTOR_ALLOWANCE = 10
        const val ZOOMED_IN = 3.0f
        const val ZOOMED_OUT = 1.0f
        const val DEEP_PAGE_INDEX = 12

        val EXTRACTOR_SCREENS: List<ExtractorScreen> = listOf(
            ExtractorScreen(
                R.string.table_of_contents,
                MemoryFixtures.TOC,
                TableOfContentsActivity::class.java,
            ) {
                val contents = TableOfContentsRobot()
                contents.awaitShown(Await.LONG_TIMEOUT_MS)
                contents.awaitLoaded(Await.LONG_TIMEOUT_MS)
            },
            ExtractorScreen(
                R.string.links_in_file,
                MemoryFixtures.LINKS,
                LinksActivity::class.java,
            ) {
                LinksRobot().awaitScanFinished()
            },
            ExtractorScreen(
                R.string.user_notes_title,
                MemoryFixtures.HIGHLIGHTS,
                UserNotesActivity::class.java,
            ) {
                HighlightScreens.awaitScanFinished(UserNotesActivity::class.java)
            },
            ExtractorScreen(
                R.string.user_highlights_title,
                MemoryFixtures.HIGHLIGHTS,
                UserHighlightsActivity::class.java,
            ) {
                HighlightScreens.awaitScanFinished(UserHighlightsActivity::class.java)
            },
        )
    }
}
