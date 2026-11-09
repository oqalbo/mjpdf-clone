// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.backup

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.content.res.XmlResourceParser
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser
import java.io.File

@MediumTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class BackupRulesTest : BackupTestCase() {
    @Test
    @TestId("BKP-60")
    fun theDictionaryIsOutsideThePlatformBackupAllowlist() {
        val dictionary = seedDictionary()
        assertTrue("The seeded dictionary file was not written", dictionary.isFile)

        val fullBackup = parse(R.xml.backup_rules)
        val extraction = parse(R.xml.data_extraction_rules)

        assertTrue(
            "backup_rules.xml must parse to at least one element, otherwise every none check below passes " +
                "vacuously",
            fullBackup.isNotEmpty(),
        )
        assertTrue(
            "data_extraction_rules.xml must parse to at least one element, for the same reason",
            extraction.isNotEmpty(),
        )

        assertTrue(
            "backup_rules.xml carries an exclude element, so it is no longer an include only list " +
                "and anything not named is no longer out",
            fullBackup.none { it.tag == EXCLUDE },
        )
        assertTrue(
            "data_extraction_rules.xml carries an exclude element",
            extraction.none { it.tag == EXCLUDE },
        )

        val fileIncludes = fullBackup
            .filter { it.tag == INCLUDE && it.domain == FILE_DOMAIN }
            .map { it.path }
        assertEquals(
            "The file domain includes in backup_rules.xml decide which private files leave the device. " +
                "domain and path are read without a namespace prefix, the way the platform reads them, so " +
                "an android: prefixed attribute reads as absent here exactly as it would at backup time",
            listOf(ANNOTATIONS, SIGNATURE).sorted(),
            fileIncludes.sorted(),
        )
        fileIncludes.forEach { path ->
            assertTrue(
                "The include \"$path\" is a parent of $DICTIONARIES, which would carry the dictionary " +
                    "into the backup set",
                !covers(path, DICTIONARIES),
            )
        }

        val cloudFileIncludes = extraction
            .filter { it.section == CLOUD_BACKUP && it.tag == INCLUDE && it.domain == FILE_DOMAIN }
        assertTrue(
            "The cloud-backup section declares a file domain include, it declares " +
                cloudFileIncludes.map { it.path },
            cloudFileIncludes.isEmpty(),
        )

        val allIncludes = (fullBackup + extraction).filter { it.tag == INCLUDE && it.domain == FILE_DOMAIN }
        allIncludes.forEach { entry ->
            assertTrue(
                "The dictionary at ${dictionary.absolutePath} is covered by the ${entry.section} " +
                    "include \"${entry.path}\", so a large optional download would eat the backup quota",
                !covers(entry.path, DICTIONARIES),
            )
        }
    }

    private fun seedDictionary(): File {
        val target = File(File(TestEnvironment.targetContext.filesDir, DICTIONARIES), DICTIONARY_NAME)
        target.parentFile?.mkdirs()
        target.writeBytes(ByteArray(DICTIONARY_BYTES))
        return target
    }

    private fun covers(includePath: String, candidate: String): Boolean {
        val normalized = includePath.trim().trimEnd('/')
        return normalized == "." || normalized == candidate || candidate.startsWith("$normalized/")
    }

    private fun parse(resourceId: Int): List<RuleEntry> {
        val entries = mutableListOf<RuleEntry>()
        val parser: XmlResourceParser = TestEnvironment.targetContext.resources.getXml(resourceId)
        var section = ""
        parser.use {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (val tag = parser.name) {
                        INCLUDE, EXCLUDE -> entries.add(
                            RuleEntry(
                                tag = tag,
                                section = section,
                                domain = parser.getAttributeValue(NO_NAMESPACE, DOMAIN).orEmpty(),
                                path = parser.getAttributeValue(NO_NAMESPACE, PATH).orEmpty(),
                            )
                        )
                        else -> section = tag
                    }
                }
                event = parser.next()
            }
        }
        return entries
    }

    private data class RuleEntry(
        val tag: String,
        val section: String,
        val domain: String,
        val path: String,
    )

    private companion object {
        const val INCLUDE = "include"
        const val EXCLUDE = "exclude"
        const val DOMAIN = "domain"
        const val PATH = "path"
        const val FILE_DOMAIN = "file"
        const val CLOUD_BACKUP = "cloud-backup"
        const val ANNOTATIONS = "annotations"
        const val SIGNATURE = "signature"
        const val DICTIONARIES = "dictionaries"
        const val DICTIONARY_BYTES = 4 * 1024 * 1024
        const val DICTIONARY_NAME = "en-wordnet.db"
        val NO_NAMESPACE: String? = null
    }
}
