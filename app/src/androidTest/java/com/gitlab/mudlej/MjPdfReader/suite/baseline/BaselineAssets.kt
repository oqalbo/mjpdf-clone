// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.baseline

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import java.security.MessageDigest
import org.json.JSONObject

object BaselineAssets {

    const val ROOT = "baselines"
    const val MANIFEST_FILE = "manifest.json"
    const val PNG_SUFFIX = ".png"
    private const val APPROVED_COUNT = 62

    private data class ApprovedProfile(val hashes: Map<String, String>)

    private val approvedProfiles = mutableMapOf<String, ApprovedProfile>()

    fun goldenPath(profile: String, goldenName: String): String =
        "$ROOT/$profile/${withPngSuffix(goldenName)}"

    fun withPngSuffix(goldenName: String): String =
        if (goldenName.endsWith(PNG_SUFFIX, ignoreCase = true)) goldenName else goldenName + PNG_SUFFIX

    fun availableProfiles(): List<String> =
        runCatching { TestEnvironment.testContext.assets.list(ROOT)?.toList().orEmpty() }
            .getOrDefault(emptyList())
            .sorted()

    fun goldenNames(profile: String): List<String> =
        runCatching { TestEnvironment.testContext.assets.list("$ROOT/$profile")?.toList().orEmpty() }
            .getOrDefault(emptyList())
            .filter { it.endsWith(PNG_SUFFIX, ignoreCase = true) }
            .sorted()

    fun goldenExists(profile: String, goldenName: String): Boolean {
        if (!availableProfiles().contains(profile)) return false
        val filename = withPngSuffix(goldenName)
        if (!approvedProfile(profile).hashes.containsKey(filename)) return false
        readApprovedBytes(profile, goldenName)
        return true
    }

    fun manifestOf(profile: String): String? =
        runCatching {
            TestEnvironment.testContext.assets.open("$ROOT/$profile/$MANIFEST_FILE")
                .use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull()

    fun readGolden(profile: String, goldenName: String): Bitmap {
        requireProfileRecorded(profile)
        val path = goldenPath(profile, goldenName)
        val bytes = readApprovedBytes(profile, goldenName)
        return decode(bytes, path)
    }

    fun decode(bytes: ByteArray, describedAs: String): Bitmap {
        val options = BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: throw BaselineMissingGolden(
                "$describedAs is not a decodable PNG. It is ${bytes.size} bytes long."
            )
    }

    private fun requireProfileRecorded(profile: String) {
        if (availableProfiles().contains(profile)) {
            approvedProfile(profile)
            return
        }
        val available = availableProfiles()
        if (available.isEmpty()) {
            throw BaselineMissingGolden(
                    "No goldens are committed at all: the test APK carries no $ROOT directory. Record them with " +
                    "an instrumentation run under ${BaselineRecording.ARGUMENT}=true on a device pinned to the " +
                    "profile, then copy the staged images into " +
                    "testing/goldens/$ROOT/$profile/ for review. This device resolves to profile $profile."
            )
        }
        throw BaselinePinMismatch(
            "This device resolves to profile $profile and no goldens are recorded for it. The committed " +
                "profiles are $available. Comparing across profiles is refused rather than attempted, because " +
                "a different density, display size, navigation mode, locale or GPU moves every pixel and the " +
                "resulting diff would accuse the app of a change it did not make. Either run on a device " +
                "matching a committed profile, or record this one."
        )
    }

    private fun readApprovedBytes(profile: String, goldenName: String): ByteArray {
        val filename = withPngSuffix(goldenName)
        val expected = approvedProfile(profile).hashes[filename]
            ?: throw missingGolden(profile, goldenName)
        val path = goldenPath(profile, goldenName)
        val bytes = TestEnvironment.testContext.assets.open(path).use { it.readBytes() }
        val actual = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString(separator = "") { "%02x".format(it) }
        if (actual != expected) {
            throw BaselineMissingGolden(
                "Golden $path has SHA-256 $actual, but its approved manifest requires $expected. " +
                    "Refusing pixels that are absent from the approved set."
            )
        }
        return bytes
    }

    private fun approvedProfile(profile: String): ApprovedProfile = synchronized(approvedProfiles) {
        approvedProfiles[profile] ?: loadApprovedProfile(profile).also { approvedProfiles[profile] = it }
    }

    private fun loadApprovedProfile(profile: String): ApprovedProfile {
        val path = "$ROOT/$profile/$MANIFEST_FILE"
        val text = manifestOf(profile)
            ?: throw BaselineMissingGolden("Profile $profile has no $MANIFEST_FILE at $path.")
        val manifest = runCatching { JSONObject(text) }.getOrElse { error ->
            throw BaselineMissingGolden("Profile $profile has an invalid $MANIFEST_FILE: $error")
        }
        if (manifest.optString("approvalStatus") != "approved") {
            throw BaselineMissingGolden(
                "Profile $profile is not approved; $path declares approvalStatus=" +
                    "${manifest.optString("approvalStatus", "<missing>")}."
            )
        }
        val declaredProfile = manifest.optString("profile")
        if (declaredProfile != profile) {
            throw BaselinePinMismatch(
                "Golden directory profile $profile disagrees with $path, which declares $declaredProfile."
            )
        }
        val count = manifest.optInt("count", -1)
        val images = manifest.optJSONArray("images")
            ?: throw BaselineMissingGolden("Approved profile $profile has no images array in $path.")
        if (count != APPROVED_COUNT || images.length() != APPROVED_COUNT) {
            throw BaselineMissingGolden(
                "Approved profile $profile must declare exactly $APPROVED_COUNT images; $path declares " +
                    "$count and contains ${images.length()} entries."
            )
        }
        val hashes = buildMap {
            for (index in 0 until images.length()) {
                val image = images.optJSONObject(index)
                    ?: throw BaselineMissingGolden("Image entry $index in $path is not an object.")
                val filename = image.optString("filename")
                val sha256 = image.optString("sha256").lowercase()
                if (!filename.endsWith(PNG_SUFFIX) || '/' in filename ||
                    !sha256.matches(Regex("[0-9a-f]{64}")) || put(filename, sha256) != null
                ) {
                    throw BaselineMissingGolden("Image entry $index in $path is invalid or duplicated: $filename.")
                }
            }
        }
        return ApprovedProfile(hashes)
    }

    private fun missingGolden(profile: String, goldenName: String): BaselineMissingGolden {
        val recorded = goldenNames(profile)
        return BaselineMissingGolden(
            "Profile $profile has no golden named ${withPngSuffix(goldenName)}. It holds ${recorded.size} " +
                "golden(s): $recorded. Record the missing one with ${BaselineRecording.ARGUMENT}=true rather " +
                "than drawing it by hand."
        )
    }
}
