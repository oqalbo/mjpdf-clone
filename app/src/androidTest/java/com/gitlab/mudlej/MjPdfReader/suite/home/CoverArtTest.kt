// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.home

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeGridSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.max

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class CoverArtTest {

    @get:Rule
    val rules = SuiteRules.standard()

    @get:Rule
    val homeCleanup = HomeCleanupRule()

    private val home = HomeRobot()

    @Test
    @TestId("HOME-39")
    fun coverArtIsRenderedAndWrittenToTheDiskCache() {
        val library = HomeBaselines.scopedLibrary(
            rules.testDirectory.directory(),
            BookSeed(HomeFixtures.ALPHA, pages = 1, lastOpened = daysAgo(0)),
        )

        home.launchFromLauncher()

        val grid = home.library()
        grid.awaitContains("alpha")

        grid.cell("alpha").assertCoverCentreIs(HomeFixtures.ALPHA_RED)

        val hash = library.hashOf("alpha")
        val bucket = Covers.gridBucket(grid.spanCount())
        val expected = Covers.fileFor(hash, bucket)
        Await.until(
            "the rendered cover is written to ${expected.name}, cache holds ${Covers.names()}"
        ) { expected.exists() }

        val strayWidths = Covers.filesFor(hash)
            .map { it.name }
            .filter { name -> Covers.buckets.none { name == Covers.fileFor(hash, it).name } }
        assertEquals(
            "every cached cover sits at a declared bucket width, cache holds ${Covers.names()}",
            emptyList<String>(),
            strayWidths,
        )
        assertTrue(
            "the atomic rename must leave no temporary file behind, cache holds ${Covers.names()}",
            Covers.temporaryFiles().isEmpty(),
        )
    }

    @Test
    @TestId("HOME-40")
    fun aCachedCoverIsUsedInsteadOfReRendering() {
        val library = HomeBaselines.scopedLibrary(
            rules.testDirectory.directory(),
            BookSeed(HomeFixtures.ALPHA, pages = 1, lastOpened = daysAgo(0)),
        )

        val bucket = Covers.gridBucket(expectedSpanCount())
        Covers.plantSentinel(library.hashOf("alpha"), bucket, HomeFixtures.GAMMA_BLUE)

        home.launchFromLauncher()

        val grid = home.library()
        grid.awaitContains("alpha")

        assertEquals(
            "the planted sentinel must sit at the bucket the grid asks for",
            bucket,
            Covers.gridBucket(grid.spanCount()),
        )
        grid.cell("alpha").assertCoverCentreIs(HomeFixtures.GAMMA_BLUE)
    }

    @Test
    @TestId("HOME-41")
    fun aCorruptPdfLeavesAnEmptyCoverAndNoCacheFile() {
        val library = HomeBaselines.scopedLibrary(
            rules.testDirectory.directory(),
            BookSeed(CORRUPT, pages = 1),
            BookSeed(HomeFixtures.ALPHA, pages = 1),
        )

        home.launchFromLauncher()

        val grid = home.library()
        grid.awaitTitles(listOf("alpha", "corrupt"))

        grid.cell("alpha").assertCoverCentreIs(HomeFixtures.ALPHA_RED)

        val corrupt = grid.cell("corrupt")
        assertEquals("the unreadable book still lists under its own title", "corrupt", corrupt.title())
        corrupt.awaitNoCover()
        assertEquals(
            "no cover may be cached for a document pdfium refuses, cache holds ${Covers.names()}",
            emptyList<String>(),
            Covers.filesFor(library.hashOf("corrupt")).map { it.name },
        )
    }

    private fun expectedSpanCount(): Int {
        val widthDp = TestEnvironment.targetContext.resources.configuration.screenWidthDp
        return max(2, widthDp / HomeGridSize.MEDIUM.targetCellDp)
    }

    private companion object {
        const val CORRUPT = "corrupt.pdf"
    }
}
