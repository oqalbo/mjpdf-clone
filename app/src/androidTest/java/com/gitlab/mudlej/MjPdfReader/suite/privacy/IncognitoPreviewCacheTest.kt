// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.privacy

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class IncognitoPreviewCacheTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = PrivacyReader()

    @Test
    @TestId("PRIV-06")
    fun anIncognitoOpenWritesNoPagePreviewCache() {
        val fixture = PrivacyFixtures.file(PrivacyFixtures.LARGE)
        val identity = PrivacyFixtures.identityOf(fixture)

        ActivityScenario.launch<MainActivity>(
            PrivacyReader.incognitoViewIntent(PrivacyFixtures.LARGE)
        ).use {
            reader.awaitReady()
            reader.awaitIdentity()
            traverse()
            PrivacyStores.holdNoPreviewsFor(identity)
            assertFalse(
                "an incognito session must leave no rendered page image on disk for $identity",
                PrivacyStores.hasPreviewsFor(identity),
            )
        }

        ActivityScenario.launch<MainActivity>(PrivacyReader.viewIntent(PrivacyFixtures.LARGE)).use {
            reader.awaitReady()
            reader.awaitIdentity()
            traverse()
            Await.until(
                "the same traversal without the incognito extra does write a preview for $identity",
                Await.LONG_TIMEOUT_MS,
            ) { PrivacyStores.hasPreviewsFor(identity) }
            assertTrue(
                "the control pass must produce previews, otherwise the incognito assertion proves nothing",
                PrivacyStores.hasPreviewsFor(identity),
            )
        }
    }

    private fun traverse() {
        PAGE_STOPS.forEach { pageIndex ->
            reader.jumpToPageIndex(pageIndex)
        }
    }

    private companion object {
        val PAGE_STOPS = listOf(4, 9, 14, 19, 24, 29)
    }
}
