// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.home

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.FileDescriptors
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.StubSharerProvider
import com.gitlab.mudlej.MjPdfReader.ui.home.Availability
import com.gitlab.mudlej.MjPdfReader.ui.home.AvailabilityProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class AvailabilityProbeTest {

    @Test
    @TestId("HOME-74")
    fun contentProbeChecksOpenabilityOffMainAndClosesEveryDescriptor() {
        val context = TestEnvironment.targetContext
        val name = "availability-probe.pdf"
        val seedUri = StubSharerProvider.uriFor(name)
        context.contentResolver.acquireContentProviderClient(seedUri)?.close()
            ?: throw AssertionError("${StubSharerProvider.AUTHORITY} is not installed")

        try {
            val uri = StubSharerProvider.publish(name, "%PDF-1.4 probe".toByteArray())
            val backing = StubSharerProvider.resolveFile(name)
                ?: throw AssertionError("the stub provider did not stage $name")
            val probe = AvailabilityProbe(context, hasFullAccess = true)

            repeat(PROBE_REPETITIONS) {
                val availability = runBlocking {
                    withContext(Dispatchers.IO) { probe.availabilityOf(uri) }
                }
                assertEquals(Availability.AVAILABLE, availability)
                FileDescriptors.awaitCount(backing, 0)
            }

            StubSharerProvider.clear()
            val missing = runBlocking {
                withContext(Dispatchers.IO) { probe.availabilityOf(uri) }
            }
            assertEquals(Availability.MISSING, missing)
        } finally {
            StubSharerProvider.clear()
        }
    }

    private companion object {
        const val PROBE_REPETITIONS = 5
    }
}
