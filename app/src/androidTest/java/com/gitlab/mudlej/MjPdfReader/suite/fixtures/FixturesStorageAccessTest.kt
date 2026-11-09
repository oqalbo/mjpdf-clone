// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.fixtures

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.os.Build
import com.gitlab.mudlej.MjPdfReader.BuildConfig
import com.gitlab.mudlej.MjPdfReader.core.PermissionManager
import com.gitlab.mudlej.MjPdfReader.core.debug.StorageAccessOverride
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FixturesStorageAccessTest {

    private var hostGranted = false
    private var initialized = false

    @Before
    fun verifyHostStorageAccess() {
        assertTrue("Storage access overrides require a debug build", BuildConfig.DEBUG)
        assertEquals(BuildConfig.APPLICATION_ID, TestEnvironment.targetContext.packageName)
        hostGranted = when (InstrumentationRegistry.getArguments().getString("storageAccess")) {
            "granted" -> true
            "denied" -> false
            else -> throw AssertionError("Explicit storageAccess=granted or storageAccess=denied is required")
        }
        assertEquals("Real storage access must match the host argument", hostGranted, Fixtures.hasAllFilesAccess())
        StorageAccessOverride.clear()
        assertFalse(StorageAccessOverride.isForced())
        assertEquals(hostGranted, PermissionManager.hasFullAccess(TestEnvironment.targetContext))
        initialized = true
    }

    @After
    fun clearOverrideAndVerifyHostStorageAccessIsUnchanged() {
        StorageAccessOverride.clear()
        if (!initialized) return
        assertEquals("Real storage access must remain unchanged", hostGranted, Fixtures.hasAllFilesAccess())
        assertFalse(StorageAccessOverride.isForced())
        assertEquals(hostGranted, PermissionManager.hasFullAccess(TestEnvironment.targetContext))
    }

    @Test
    fun shellOwnedMediaScanPreservesTheBackingFileAndCanBeRepeated() {
        assumeTrue(hostGranted)
        val source = Fixtures.seededFixture("normal.pdf")
        val target = File(Fixtures.perTestRoot, "shell-media-scan-regression.pdf")
        target.parentFile!!.mkdirs()
        try {
            source.copyTo(target, overwrite = true)
            Fixtures.scanAndWait(target)
            Fixtures.replaceWithShellCopy(source, target)
            repeat(2) {
                val uri = Fixtures.scanFromShellAndWait(target)
                assertTrue("Scanning must preserve the shell-created PDF", Fixtures.shellFileExists(target))
                assertEquals(source.length(), target.length())
                assertTrue(Fixtures.contentUriCanOpen(uri))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    assertTrue(Fixtures.mediaStoreOwnerPackage(uri) != TestEnvironment.targetPackage)
                }
            }
        } finally {
            Fixtures.delete(target)
        }
    }

    @Test
    fun grantAndEnsureRequireARealHostGrantAndClearOverrides() {
        val operations = listOf(
            "grantAllFilesAccess" to { Fixtures.grantAllFilesAccess() },
            "ensureAllFilesAccess" to { Fixtures.ensureAllFilesAccess() },
        )
        for ((name, requireAccess) in operations) {
            StorageAccessOverride.force(!hostGranted)
            assertTrue(StorageAccessOverride.isForced())
            assertEquals(!hostGranted, PermissionManager.hasFullAccess(TestEnvironment.targetContext))

            if (hostGranted) {
                requireAccess()
            } else {
                val failure = assertThrows(AssertionError::class.java) { requireAccess() }
                assertTrue(
                    "$name must identify the host requirement: ${failure.message}",
                    failure.message?.contains("host") == true,
                )
            }

            assertFalse("$name must clear the override", StorageAccessOverride.isForced())
            assertEquals("$name must preserve real access", hostGranted, Fixtures.hasAllFilesAccess())
            assertEquals(hostGranted, PermissionManager.hasFullAccess(TestEnvironment.targetContext))
        }
    }

    @Test
    fun simulateNoAllFilesAccessRequiresARealHostGrant() {
        if (hostGranted) {
            Fixtures.simulateNoAllFilesAccess()
            assertFalse(PermissionManager.hasFullAccess(TestEnvironment.targetContext))
            assertTrue(Fixtures.hasAllFilesAccess())
            assertTrue(StorageAccessOverride.isForced())
            Fixtures.stopSimulatingAccess()
        } else {
            val failure = assertThrows(AssertionError::class.java) { Fixtures.simulateNoAllFilesAccess() }
            assertTrue(
                "Simulation must identify the host requirement: ${failure.message}",
                failure.message?.contains("host") == true,
            )
        }

        assertFalse(StorageAccessOverride.isForced())
        assertEquals(hostGranted, Fixtures.hasAllFilesAccess())
        assertEquals(hostGranted, PermissionManager.hasFullAccess(TestEnvironment.targetContext))
    }

    @Test
    fun denyAllFilesAccessOnlyChangesTheAppViewUntilStopped() {
        Fixtures.denyAllFilesAccess()

        assertFalse(PermissionManager.hasFullAccess(TestEnvironment.targetContext))
        assertTrue(StorageAccessOverride.isForced())
        assertEquals(hostGranted, Fixtures.hasAllFilesAccess())

        Fixtures.stopSimulatingAccess()

        assertFalse(StorageAccessOverride.isForced())
        assertEquals(hostGranted, Fixtures.hasAllFilesAccess())
        assertEquals(hostGranted, PermissionManager.hasFullAccess(TestEnvironment.targetContext))
    }

    @Test
    fun resetClearsEitherOverrideIdempotentlyWithoutAHostGrant() {
        for (forcedAccess in listOf(true, false)) {
            StorageAccessOverride.force(forcedAccess)
            assertTrue(StorageAccessOverride.isForced())
            assertEquals(forcedAccess, PermissionManager.hasFullAccess(TestEnvironment.targetContext))
            assertEquals(hostGranted, Fixtures.hasAllFilesAccess())

            repeat(2) {
                Fixtures.stopSimulatingAccess()

                assertFalse(StorageAccessOverride.isForced())
                assertEquals("Reset must preserve real access", hostGranted, Fixtures.hasAllFilesAccess())
                assertEquals(hostGranted, PermissionManager.hasFullAccess(TestEnvironment.targetContext))
            }
        }
    }
}
