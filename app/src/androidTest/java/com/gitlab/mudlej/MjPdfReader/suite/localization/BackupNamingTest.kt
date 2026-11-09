// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.localization

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationDialogs
import com.gitlab.mudlej.MjPdfReader.suite.backup.BackupTree
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class BackupNamingTest : LocaleTestCase() {

    @Test
    @TestId("LOC-27")
    fun aManualBackupKeepsAsciiDigitsInItsNameUnderPersian() {
        useLocale(PERSIAN)
        val tree = ownTree()
        LocaleRecords.seed("loc27")

        LocaleBackup.launchBackupPage().use {
            LocaleBackup.grantFolder(tree)
            LocaleBackup.exportWithDefaults()

            val written = BackupTree.awaitManualBackup(tree)
            assertEquals(
                "exactly one manual backup must be written into this test's own tree, it held " +
                    "${BackupTree.fileNames(tree)}",
                1,
                BackupTree.manualBackups(tree).size,
            )
            assertTrue(
                "BackupFolder.timestampedFileName formats with Locale.US so the name stays machine readable " +
                    "on a Persian phone. It read \"${written.name}\".",
                LocaleBackup.asciiManualName.matches(written.name),
            )
            assertFalse(
                "the name must carry no Persian digit in U+06F0 to U+06F9, it read \"${written.name}\"",
                LocaleFixtures.containsPersianDigit(written.name),
            )
            assertFalse(
                "the name must carry no Arabic-Indic digit in U+0660 to U+0669, it read \"${written.name}\"",
                LocaleFixtures.containsArabicIndicDigit(written.name),
            )

            val report = LocaleBackup.awaitExportReport()
            assertTrue(
                "the export report must name the saved file in its Persian wording. Expected it to contain " +
                    "\"${localized(R.string.backup_export_saved_file, written.name)}\", it read \"$report\".",
                report.contains(localized(R.string.backup_export_saved_file, written.name)),
            )
            LocaleBackup.dismissExportReport()
        }
    }

    @Test
    @TestId("LOC-28")
    fun retentionStillPrunesLegacyPersianDigitAutoBackupNames() {
        val tree = ownTree()
        val seeded = seedMixedDigitAutoBackups(tree)
        val oldest = seeded.first()
        val newestPersian = seeded.filter { it.persian }.last()

        assertEquals(
            "the tree must start with exactly $SEEDED_AUTO_BACKUPS seeded auto backups",
            SEEDED_AUTO_BACKUPS,
            BackupTree.autoBackups(tree).size,
        )

        LocaleBackup.launchBackupPage().use {
            LocaleBackup.grantFolder(tree)
            LocaleBackup.exportWithDefaults()
            BackupTree.awaitManualBackup(tree)

            val retained = Await.untilValue(
                "retention prunes the auto backups down to $RETAINED_AUTO_BACKUPS",
                Await.LONG_TIMEOUT_MS,
                read = { BackupTree.autoBackups(tree).map { file -> file.name } },
            ) { it.size == RETAINED_AUTO_BACKUPS }

            assertFalse(
                "the single oldest file must be the one deleted, and it was named with Persian digits. " +
                    "Retained names were $retained.",
                retained.contains(oldest.name),
            )
            assertTrue(
                "autoBackupNameRegex uses \\p{Nd}, so a Persian digit name is recognised for pruning. " +
                    "The oldest name \"${oldest.name}\" carries Persian digits and had to go.",
                LocaleBackup.hasNonAsciiDigit(oldest.name),
            )
            assertTrue(
                "every file newer than the oldest must survive, so the newest Persian digit name " +
                    "\"${newestPersian.name}\" must still be present in $retained",
                retained.contains(newestPersian.name),
            )
            seeded.drop(1).forEach { seed ->
                assertTrue(
                    "retention keeps the ten newest by modification time regardless of digit set, so " +
                        "\"${seed.name}\" must survive. Retained names were $retained.",
                    retained.contains(seed.name),
                )
            }

            LocaleBackup.awaitExportReport()
            LocaleBackup.dismissExportReport()
        }
    }

    @Test
    @TestId("LOC-29")
    fun arabicLocalizesCountsOnScreenWhileFileNamesStayAscii() {
        useLocale(ARABIC)
        val tree = ownTree()
        val titles = LocaleRecords.seedMany(ARABIC_RECORDS).map { it.fileName }

        val body = localizedPlural(R.plurals.home_delete_batch_confirm_message, ARABIC_RECORDS, ARABIC_RECORDS)
        assertTrue(
            "getQuantityString formats with the resource locale, so an Arabic count must render with " +
                "Arabic-Indic digits. The body read \"$body\".",
            LocaleFixtures.containsArabicIndicDigit(body),
        )

        launchHome().use {
            LocaleHome.selectItems(home, titles)
            LocaleHome.tapDelete(body)

            assertTrue(
                "the delete confirmation must show the Arabic digit body \"$body\", visible text was " +
                    "${visibleTexts()}",
                TestEnvironment.uiDevice.wait(Until.hasObject(By.text(body)), Await.LONG_TIMEOUT_MS) == true,
            )
            AnnotationDialogs.pressNegative()
        }

        LocaleBackup.launchBackupPage().use {
            LocaleBackup.grantFolder(tree)
            LocaleBackup.exportWithDefaults()

            val written = BackupTree.awaitManualBackup(tree)
            assertTrue(
                "digits shown to the user are localized but digits written to storage are not, so the " +
                    "backup name must stay ASCII. It read \"${written.name}\".",
                LocaleBackup.asciiManualName.matches(written.name),
            )
            assertFalse(
                "the name must carry no Arabic-Indic digit, it read \"${written.name}\"",
                LocaleFixtures.containsArabicIndicDigit(written.name),
            )
            LocaleBackup.awaitExportReport()
            LocaleBackup.dismissExportReport()
        }
    }

    private data class SeededBackup(val name: String, val persian: Boolean)

    private fun ownTree() = suite.testDirectory.directory().also { BackupTree.requireEmpty(it) }

    private fun seedMixedDigitAutoBackups(tree: java.io.File): List<SeededBackup> {
        val base = System.currentTimeMillis() - SEEDED_AUTO_BACKUPS * MINUTE_MILLIS * 2
        return (0 until SEEDED_AUTO_BACKUPS).map { rank ->
            val persian = rank % 2 == 0
            val stamp = "2026%02d%02d-000000".format(1, rank + 1)
            val name = LocaleBackup.autoBackupName(
                if (persian) LocaleBackup.toPersianDigits(stamp) else stamp
            )
            LocaleBackup.seedAutoBackup(tree, name, base + rank * MINUTE_MILLIS)
            SeededBackup(name, persian)
        }
    }

    private companion object {
        const val SEEDED_AUTO_BACKUPS = 11
        const val RETAINED_AUTO_BACKUPS = 10
        const val ARABIC_RECORDS = 5
        const val MINUTE_MILLIS = 60_000L
    }
}
