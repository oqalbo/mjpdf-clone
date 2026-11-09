// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.home

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.data.AppDatabase
import com.gitlab.mudlej.MjPdfReader.data.PdfRepository
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.StubSharerProvider
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.home.Availability
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeTab
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class HomeRecordLoadingTest {

    @get:Rule
    val rules = SuiteRules.standard()

    @get:Rule
    val homeCleanup = HomeCleanupRule()

    @Test
    fun readableLibraryRecordIsUsableWhileRecentWaitsForAContentProvider() {
        val library = HomeBaselines.indexedLibrary(
            BookSeed(HomeFixtures.ALPHA, pages = 1, lastOpened = daysAgo(1)),
            BookSeed(HomeFixtures.BANANA, indexed = false, pages = 1, lastOpened = daysAgo(2)),
        )
        val name = "home-record-gated.pdf"
        val context = TestEnvironment.targetContext
        requireNotNull(context.contentResolver.acquireContentProviderClient(StubSharerProvider.uriFor(name))).close()
        val contentUri = StubSharerProvider.registerPath(name, library.fileOf("banana").absolutePath)
        HomeLibrary.replaceRecordUri(library.hashOf("banana"), contentUri)
        val opens = AtomicInteger()
        StubSharerProvider.blockFirstOpenMatching(name) {
            opens.incrementAndGet()
            true
        }
        try {
            val home = HomeRobot().launchFromLauncher()
            assertTrue(
                "the provider-backed record holds its first open",
                StubSharerProvider.awaitOpenBlocked(name, Await.LONG_TIMEOUT_MS),
            )
            home.selectTab(HomeTab.LIBRARY)
            home.library().awaitContains("alpha")
            val local = home.library().item("alpha")
            assertFalse("the local book is served from its record", local.isScanOnly)
            assertEquals("the local book is available", Availability.AVAILABLE, local.availability)
            assertEquals("the local book is not dimmed", 1.0f, home.library().cell("alpha").alpha(), 0.01f)
            assertTrue("Recent publishes no rows while the provider is blocked", home.recent().items().isEmpty())
            assertTrue("Continue Reading stays empty while the provider is blocked", home.recent().heroItems().isEmpty())
            assertEquals("the blocked provider is opened exactly once", 1, opens.get())

            runBlocking {
                PdfRepository(AppDatabase.getInstance(context)).setFavorite(library.hashOf("alpha"), true)
            }
            Await.until("Library applies record metadata while the provider remains blocked") {
                home.library().item("alpha").let { it.favorite && it.availability == Availability.AVAILABLE }
            }
            assertTrue("Recent still publishes no rows", home.recent().items().isEmpty())
            assertEquals("the favorite update does not reopen the blocked provider", 1, opens.get())

            StubSharerProvider.releaseBlockedOpen(name)
            home.selectTab(HomeTab.RECENT)
            Await.until("Recent commits both access-validated records together") {
                val items = home.recent().items()
                items.map { it.hash }.toSet() == library.hashes.toSet() &&
                    items.all { it.availability == Availability.AVAILABLE } &&
                    items.single { it.hash == library.hashOf("alpha") }.favorite
            }
        } finally {
            StubSharerProvider.releaseBlockedOpen(name)
            StubSharerProvider.clear()
        }
    }
}
