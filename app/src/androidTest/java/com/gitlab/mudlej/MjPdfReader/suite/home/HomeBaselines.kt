// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.home

import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.home.ScanMode
import java.io.File

object HomeBaselines {

    const val SEALED_DIRECTORY_NAME = "home-library"

    val testBooksRoot: File = File(Fixtures.externalRoot, "TestBooks")

    val sealedDirectory: File
        get() {
            val base = TestEnvironment.targetContext.getExternalFilesDir(null)
                ?: throw AssertionError(
                    "The app has no external files directory, so the sealed home baseline has nowhere to put " +
                        "fixtures that stay readable once all files access is denied."
                )
            return File(base, SEALED_DIRECTORY_NAME).also { it.mkdirs() }
        }

    fun cleanHome() {
        HomeLibrary.clearRecords()
        denyAccess()
    }

    fun recordsOnly(vararg seeds: BookSeed): SeededLibrary =
        sealed(seeds.map { it.copy(indexed = false) })

    fun indexedLibrary(vararg seeds: BookSeed): SeededLibrary = sealed(seeds.toList())

    fun indexedTree(vararg seeds: BookSeed): SeededLibrary {
        rejectRecordedSeeds(seeds)
        Fixtures.ensureAllFilesAccess()
        val library = HomeLibrary.seedInPlace(testBooksRoot, seeds.toList())
        SettingsSeeder.seed(Preferences.scanModeKey, ScanMode.WHOLE_DEVICE.name)
        denyAccess()
        return library
    }

    private fun rejectRecordedSeeds(seeds: Array<out BookSeed>) {
        val recorded = seeds.filter { it.record }
        if (recorded.isEmpty()) return
        throw AssertionError(
            "indexedTree cannot seed a record for ${recorded.joinToString { it.name }}. It denies all files " +
                "access once the files are hashed, which is what freezes the index, but MJ PDF then cannot " +
                "inspect a PdfRecord pointing at ${testBooksRoot.absolutePath}. AvailabilityProbe reports " +
                "ACCESS_UNKNOWN rather than claiming the file exists or is missing. The row would render " +
                "dimmed at 0.45 and the Recent tab would raise the permission card, which reads as a product " +
                "bug rather than a baseline artifact. Scan only seeds are unaffected because HomeItem.fromScan " +
                "never probes. Use scopedLibrary if you want a recorded book that resolves as AVAILABLE, or " +
                "use the real-access wrappers when ACCESS_UNKNOWN is the state under test."
        )
    }

    fun scopedLibrary(directory: File, vararg seeds: BookSeed): SeededLibrary {
        Fixtures.ensureAllFilesAccess()
        val library = HomeLibrary.seedInto(directory, seeds.toList())
        SettingsSeeder.seedAll(
            mapOf(
                Preferences.scanModeKey to ScanMode.SELECTED_LOCATIONS.name,
                Preferences.scanLocationsKey to setOf(directory.absolutePath),
            )
        )
        return library
    }

    fun grantAccess() {
        Fixtures.stopSimulatingAccess()
        Fixtures.ensureAllFilesAccess()
    }

    fun denyAccess() {
        Fixtures.simulateNoAllFilesAccess()
    }

    fun simulateDeniedAccess(probeFile: File) {
        Fixtures.denyAllFilesAccess(probeFile)
    }

    private fun sealed(seeds: List<BookSeed>): SeededLibrary {
        Fixtures.ensureAllFilesAccess()
        val library = HomeLibrary.seedInto(sealedDirectory, seeds)
        SettingsSeeder.seed(Preferences.scanModeKey, ScanMode.WHOLE_DEVICE.name)
        denyAccess()
        return library
    }
}
