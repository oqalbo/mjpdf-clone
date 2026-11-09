// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.baseline

import android.os.Build
import com.gitlab.mudlej.MjPdfReader.suite.core.Shell
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment

data class DisplayPin(val width: Int, val height: Int) {
    val asProfileSegment: String get() = "${width}x$height"

    override fun toString(): String = asProfileSegment
}

data class DevicePins(
    val apiLevel: Int,
    val display: DisplayPin,
    val densityDpi: Int,
    val navigationMode: String,
    val locale: String,
    val gpuRenderer: String,
    val fontScale: Float,
    val nightMode: String,
    val timeZone: String,
) {

    val gpuSegment: String get() = if (isSwiftShader) SWIFTSHADER else UNPINNED

    val isSwiftShader: Boolean get() = gpuRenderer.contains(SWIFTSHADER, ignoreCase = true)

    val profile: String
        get() = "api$apiLevel-${display.asProfileSegment}-${densityDpi}dpi-$navigationMode-$locale-$gpuSegment"

    fun describe(): String = buildString {
        appendLine("profile: $profile")
        appendLine("apiLevel: $apiLevel")
        appendLine("display: ${display.asProfileSegment}")
        appendLine("densityDpi: $densityDpi")
        appendLine("navigationMode: $navigationMode")
        appendLine("locale: $locale")
        appendLine("gpuRenderer: $gpuRenderer")
        appendLine("fontScale: $fontScale")
        appendLine("nightMode: $nightMode")
        appendLine("timeZone: $timeZone")
    }

    companion object {
        const val SWIFTSHADER = "swiftshader"
        const val UNPINNED = "unpinned"
    }
}

object BaselineProfile {

    const val THREE_BUTTON = "3button"
    const val GESTURE = "gesture"
    const val UNKNOWN_NAVIGATION = "navunknown"

    private const val THREE_BUTTON_OVERLAY = "com.android.internal.systemui.navbar.threebutton"
    private const val GESTURAL_OVERLAY = "com.android.internal.systemui.navbar.gestural"

    @Volatile
    private var cached: DevicePins? = null

    fun pins(): DevicePins = cached ?: synchronized(this) {
        cached ?: read().also { cached = it }
    }

    fun current(): String = pins().profile

    fun forget() {
        synchronized(this) { cached = null }
    }

    fun requireSwiftShader() {
        val pins = pins()
        if (pins.isSwiftShader) return
        throw BaselinePinMismatch(
            "The GPU is not swiftshader, it reports \"${pins.gpuRenderer}\". Host GPU drivers do not rasterise " +
                "byte identically across machines, so goldens recorded or compared under one are worthless. " +
                "Restart the emulator with -gpu swiftshader_indirect and record the goldens again. " +
                "The profile this device would resolve to is ${pins.profile}."
        )
    }

    private fun read(): DevicePins = DevicePins(
        apiLevel = Build.VERSION.SDK_INT,
        display = readDisplay(),
        densityDpi = readDensity(),
        navigationMode = readNavigationMode(),
        locale = readLocale(),
        gpuRenderer = readGpuRenderer(),
        fontScale = TestEnvironment.targetContext.resources.configuration.fontScale,
        nightMode = readNightMode(),
        timeZone = java.util.TimeZone.getDefault().id,
    )

    private fun readDisplay(): DisplayPin {
        val output = Shell.executeQuietly("wm size")
        val override = valueAfter(output, "Override size:")
        val physical = valueAfter(output, "Physical size:")
        val chosen = override ?: physical
            ?: throw BaselinePinMismatch(
                "wm size reported neither an override nor a physical size, so the display pin cannot be read. " +
                    "It printed: ${output.trim()}"
            )
        val parts = chosen.split("x")
        val width = parts.getOrNull(0)?.trim()?.toIntOrNull()
        val height = parts.getOrNull(1)?.trim()?.toIntOrNull()
        if (width == null || height == null) {
            throw BaselinePinMismatch("wm size reported \"$chosen\", which is not a WIDTHxHEIGHT pair")
        }
        return DisplayPin(width, height)
    }

    private fun readDensity(): Int {
        val output = Shell.executeQuietly("wm density")
        val value = valueAfter(output, "Override density:")
            ?: valueAfter(output, "Physical density:")
        return value?.toIntOrNull()
            ?: TestEnvironment.targetContext.resources.displayMetrics.densityDpi
    }

    private fun readNavigationMode(): String {
        val enabled = Shell.executeQuietly("cmd overlay list android")
            .lineSequence()
            .filter { it.trimStart().startsWith("[x]") }
            .map { it.substringAfter("[x]").trim() }
            .toList()
        return when {
            enabled.any { it == THREE_BUTTON_OVERLAY } -> THREE_BUTTON
            enabled.any { it == GESTURAL_OVERLAY } -> GESTURE
            else -> UNKNOWN_NAVIGATION
        }
    }

    private fun readLocale(): String {
        val locale = TestEnvironment.targetContext.resources.configuration.locales[0]
        val country = locale.country
        return if (country.isNullOrEmpty()) locale.language else "${locale.language}-$country"
    }

    private fun readGpuRenderer(): String {
        val line = Shell.executeQuietly("dumpsys SurfaceFlinger")
            .lineSequence()
            .firstOrNull { it.startsWith("GLES:") }
        return line?.removePrefix("GLES:")?.trim().orEmpty().ifEmpty { "unreadable" }
    }

    private fun readNightMode(): String =
        valueAfter(Shell.executeQuietly("cmd uimode night"), "Night mode:") ?: "unreadable"

    private fun valueAfter(output: String, label: String): String? = output
        .lineSequence()
        .firstOrNull { it.trim().startsWith(label) }
        ?.substringAfter(label)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}
