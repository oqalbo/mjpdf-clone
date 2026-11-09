// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.privacy

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.net.LocalPdfServer
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class IncognitoOnlineCacheTest {

    private val suite = SuiteRules.standard()
    private val server = LocalPdfServer()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(suite).around(server)

    private val reader = PrivacyReader()

    @After
    fun removeAnyDownloadedCopy() {
        runCatching { File(PrivacyFixtures.seededRoot, DOWNLOAD_NAME).delete() }
    }

    @Test
    @TestId("PRIV-14")
    fun anIncognitoOnlineDownloadUsesTheIncognitoCacheAndIsSweptOnExit() {
        val fixture = PrivacyFixtures.file(PrivacyFixtures.NORMAL)
        val url = server.servePdfBytes("/$DOWNLOAD_NAME", fixture.readBytes())

        reader.reader.withExternalIntent(PrivacyReader.incognitoOnlineViewIntent(url)) {
            reader.reader.acceptCleartextWarning()
            reader.awaitReady()
            reader.awaitIncognitoIcon(visible = true)
            reader.awaitIdentity()

            val incognitoFiles = Await.untilValue(
                "the incognito online cache holds the downloaded document",
                Await.LONG_TIMEOUT_MS,
                read = { PrivacyStores.onlineIncognitoFiles().filter { it.name.endsWith(".pdf") } },
            ) { it.size == 1 }
            assertEquals(
                "an incognito download must land in the incognito cache alone",
                1,
                incognitoFiles.size,
            )
            assertEquals(
                "the normal online cache must stay empty during an incognito download",
                emptyList<String>(),
                PrivacyStores.onlineFiles().map { it.name },
            )
        }

        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)

        Await.until(
            "the incognito online cache is swept when the reader finishes",
            Await.LONG_TIMEOUT_MS,
        ) { PrivacyStores.onlineIncognitoFiles().isEmpty() }
        assertEquals(
            "nothing fetched from the web in incognito may outlive the session",
            emptyList<String>(),
            PrivacyStores.onlineIncognitoFiles().map { it.name },
        )
        assertTrue(
            "saveToFileAndDisplay must skip the Downloads copy while incognito",
            !File(PrivacyFixtures.seededRoot, DOWNLOAD_NAME).exists(),
        )
    }

    private companion object {
        const val DOWNLOAD_NAME = "priv-online-qxzv.pdf"
    }
}
