// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.scan

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.core.io.DocumentIdentity
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class ScanIdentityTest : ScanTestCase() {

    @Test
    @TestId("SCAN-29")
    fun twoFilesSharingTheirFirstMegabyteGetDifferentIdentities() {
        grantAccess()
        val twins = ScanFixtures.createDirectory(File(testDirectory(), "twins"))
        val twinA = ScanFixtures.copyFile(ScanFixtures.seededFile(ScanFixtures.TWIN_A), File(twins, ScanFixtures.TWIN_A))
        val twinB = ScanFixtures.copyFile(ScanFixtures.seededFile(ScanFixtures.TWIN_B), File(twins, ScanFixtures.TWIN_B))
        ScanPrefs.seedSelected(twins)

        assertEquals("The twins are equal in length", twinA.length(), twinB.length())
        assertEquals("The twins are the declared 3 MB", ScanFixtures.TWIN_BYTES, twinA.length())

        val baseline = scanBaseline()
        launchHome()
        awaitScanComplete(baseline)

        val rows = ScanCache.awaitAllHashedUnder(twins, 2)
        assertEquals("One row per path", 2, rows.size)
        rows.forEach {
            assertEquals("Both rows report the 3 MB length", ScanFixtures.TWIN_BYTES, it.size)
            ScanCache.assertCurrentIdentity(it, "The row for ${it.path}")
        }

        val hashA = ScanCache.byPath(twinA)?.hash
        val hashB = ScanCache.byPath(twinB)?.hash
        assertNotEquals(
            "Two documents that share their first megabyte and their total length must not collide. " +
                "Only the tail read separates them, which is the whole point of the h2 identity scheme",
            hashA,
            hashB,
        )

        home.library().awaitItemCount(2, Await.VERY_LONG_TIMEOUT_MS)
        assertEquals(
            "Both twins reach the grid as distinct items",
            listOf("twin-a", "twin-b"),
            home.library().titles().sorted(),
        )
    }

    @Test
    @TestId("SCAN-30")
    fun aLegacyHashLeftByAnOlderVersionIsUpgradedOnTheNextScan() {
        grantAccess()
        val bulk = ScanFixtures.createDirectory(File(testDirectory(), ScanFixtures.BULK))
        val legacyFile = ScanFixtures.copyFile(ScanFixtures.bulkMember(1), File(bulk, "legacy.pdf"))
        ScanPrefs.seedSelected(bulk)

        val seeded = ScanCache.insertLegacyRow(legacyFile, LEGACY_HASH)
        assertTrue(
            "The seeded hash must read as legacy, otherwise the upgrade branch is never entered",
            DocumentIdentity.isLegacy(seeded.hash!!),
        )

        val baseline = scanBaseline()
        launchHome()
        awaitScanComplete(baseline)

        val upgraded = ScanCache.awaitRow(
            legacyFile.absolutePath,
            "the legacy row is rewritten with a current identity",
            Await.VERY_LONG_TIMEOUT_MS,
        ) { it != null && !it.hash.isNullOrEmpty() && !DocumentIdentity.isLegacy(it.hash!!) }!!

        ScanCache.assertCurrentIdentity(upgraded, "The upgraded row")
        assertNotEquals("The legacy value was replaced", LEGACY_HASH, upgraded.hash)
        assertTrue(
            "The backfill re-reads the page count when it rewrites the hash, so the seeded zero is gone",
            upgraded.pageCount > 0,
        )
        ScanCache.awaitCountUnder(bulk, 1)
    }

    private companion object {
        const val LEGACY_HASH = "0123456789abcdef0123456789abcdef"
    }
}
