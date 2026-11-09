// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.baseline

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.rules.FailureArtifacts
import java.io.File

object BaselineRecording {

    const val ARGUMENT = "baseline.record"

    const val STAGING_DIRECTORY = "baseline-staging"

    fun isRecording(): Boolean {
        val raw = runCatching {
            InstrumentationRegistry.getArguments().getString(ARGUMENT)
        }.getOrNull() ?: return false
        return raw.equals("true", ignoreCase = true) || raw == "1"
    }

    fun stagingRoot(): File {
        Fixtures.ensureAllFilesAccess()
        val directory = File(FailureArtifacts.root(), STAGING_DIRECTORY)
        directory.mkdirs()
        return directory
    }

    fun stagingDirectoryFor(profile: String): File {
        val directory = File(stagingRoot(), profile)
        directory.mkdirs()
        return directory
    }

    fun stage(profile: String, goldenName: String, bitmap: Bitmap): File {
        val target = File(stagingDirectoryFor(profile), BaselineAssets.withPngSuffix(goldenName))
        BitmapFiles.write(bitmap, target)
        return target
    }

    fun refusalMessage(profile: String, staged: List<File>, comparison: String): String = buildString {
        appendLine(
            "Recorded ${staged.size} baseline image(s) into ${stagingDirectoryFor(profile).absolutePath}."
        )
        staged.forEach { appendLine("  ${it.name}") }
        appendLine()
        appendLine(comparison)
        appendLine()
        append(
            "This run is reported as failed on purpose. Record mode can never be mistaken for a green run and " +
                "must never be left switched on. Copy the staged images into " +
                "testing/goldens/${BaselineAssets.ROOT}/$profile/ as a reviewed act, attach the " +
                "diff for each changed golden to the merge request, and state which code change moved the " +
                "rendering. A golden that changed without a matching intentional rendering change is a bug " +
                "that was just approved by accident."
        )
    }
}
