// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.privacy

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.core.net.toUri
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.core.io.DocumentIdentity
import com.gitlab.mudlej.MjPdfReader.data.entity.PdfRecord
import com.gitlab.mudlej.MjPdfReader.data.entity.ReadingStatus
import com.gitlab.mudlej.MjPdfReader.data.entity.UserBookmark
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class IncognitoIdentityMigrationTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = PrivacyReader()

    @Test
    @TestId("PRIV-40")
    fun openingALegacyRecordDocumentInIncognitoRewritesItsIdentityKey() {
        val fixture = PrivacyFixtures.file(PrivacyFixtures.NORMAL)
        val legacyHash = PrivacyFixtures.legacyIdentityOf(fixture)
        val newHash = PrivacyFixtures.identityOf(fixture)
        assertTrue(
            "the legacy identity must not already carry the new prefix",
            DocumentIdentity.isLegacy(legacyHash),
        )

        val seededDate = LocalDateTime.parse(SEEDED_DATE)
        Records.database.pdfRecordDao().insert(
            PdfRecord(
                hash = legacyHash,
                pageNumber = SEEDED_PAGE_INDEX,
                uri = fixture.toUri(),
                length = fixture.length().toInt(),
                fileName = PrivacyFixtures.NORMAL_TITLE,
                password = null,
                lastOpened = seededDate,
                reading = ReadingStatus.UNSET,
                favorite = false,
            )
        )
        Records.database.userBookmarkDao().upsert(
            UserBookmark(
                fileHash = legacyHash,
                pageIndex = SEEDED_PAGE_INDEX,
                label = SEEDED_BOOKMARK_LABEL,
                createdAt = seededDate,
                sortOrder = 0,
            )
        )

        ActivityScenario.launch<MainActivity>(PrivacyReader.incognitoViewIntent(PrivacyFixtures.NORMAL)).use {
            reader.awaitReady()
            reader.awaitIdentity()
            Await.until("the legacy record is migrated onto the new identity", Await.LONG_TIMEOUT_MS) {
                Records.byHash(newHash) != null
            }
        }

        val rows = PrivacyStores.records()
        assertEquals("the migration must move the record rather than create one", 1, rows.size)
        val row = rows.single()
        assertTrue(
            "the migrated hash must carry the new identity prefix, it reads ${row.hash}",
            row.hash.startsWith(DocumentIdentity.PREFIX),
        )
        assertNotEquals("the migrated hash must no longer be the legacy one", legacyHash, row.hash)
        assertEquals("the migrated hash must be the identity of the file on disk", newHash, row.hash)
        assertEquals(
            "the incognito session must contribute no reading data",
            SEEDED_PAGE_INDEX,
            row.pageNumber,
        )
        assertEquals("the last opened date must be untouched", seededDate, row.lastOpened)

        val bookmarks = Records.bookmarksOf(newHash)
        assertEquals("the bookmark must be carried across to the new hash", 1, bookmarks.size)
        assertEquals(
            "the carried bookmark must keep its page",
            SEEDED_PAGE_INDEX,
            bookmarks.single().pageIndex,
        )
        assertEquals(
            "no bookmark may be left behind under the legacy hash",
            0,
            Records.bookmarksOf(legacyHash).size,
        )
    }

    private companion object {
        const val SEEDED_PAGE_INDEX = 4
        const val SEEDED_DATE = "2026-01-02T03:04:05"
        const val SEEDED_BOOKMARK_LABEL = "seeded legacy bookmark"
    }
}
