// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.navigation

import android.app.Activity
import android.media.AudioManager
import android.view.KeyEvent
import android.view.MotionEvent
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.Shell
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment

object NavigationKeys {

    fun pressVolumeDown() {
        press(KeyEvent.KEYCODE_VOLUME_DOWN)
    }

    fun pressVolumeUp() {
        press(KeyEvent.KEYCODE_VOLUME_UP)
    }

    fun pressVolumeDown(times: Int) {
        repeat(times) { pressVolumeDown() }
    }

    fun pressVolumeDownThroughShell(times: Int) {
        repeat(times) {
            Shell.executeQuietly("input keyevent ${KeyEvent.KEYCODE_VOLUME_DOWN}")
            TestEnvironment.uiDevice.waitForIdle()
        }
    }

    fun mouseForwardButton(activity: Activity): Boolean =
        NavigationDirectInput.dispatchMouseButton(activity, MotionEvent.BUTTON_FORWARD)

    fun mouseBackButton(activity: Activity): Boolean =
        NavigationDirectInput.dispatchMouseButton(activity, MotionEvent.BUTTON_BACK)

    fun mouseBackKey(activity: Activity): Boolean =
        NavigationDirectInput.dispatchMouseKey(activity, KeyEvent.KEYCODE_BACK)

    private fun press(keyCode: Int) {
        TestEnvironment.uiDevice.pressKeyCode(keyCode)
        TestEnvironment.uiDevice.waitForIdle()
    }
}

object NavigationAudio {

    fun adjustableLevels(): Map<Int, Int> = KEY_STREAMS
        .filter { manager().getStreamMaxVolume(it) > minLevelOf(it) }
        .associateWith { manager().getStreamVolume(it) }

    fun primeAllMidway(): Map<Int, Int> {
        val adjustable = KEY_STREAMS.filter { manager().getStreamMaxVolume(it) > minLevelOf(it) }
        if (adjustable.isEmpty()) {
            throw ConditionTimeoutException(
                "No stream a volume key can reach offers an adjustable range on this device, so a volume " +
                    "key test cannot tell a consumed press from an ignored one"
            )
        }
        adjustable.forEach { stream ->
            val low = minLevelOf(stream)
            val high = manager().getStreamMaxVolume(stream)
            val midway = low + (high - low) / 2
            val target = if (midway <= low) low + 1 else midway
            manager().setStreamVolume(stream, target.coerceIn(low, high), 0)
        }
        return Await.untilValue(
            condition = "every adjustable stream settles away from its bounds",
            timeoutMs = Await.SHORT_TIMEOUT_MS,
            read = { adjustableLevels() },
        ) { levels -> levels.all { (stream, value) -> value > minLevelOf(stream) } }
    }

    fun assertAllLevelsStay(baseline: Map<Int, Int>, windowMs: Long = STABLE_LEVEL_MS) {
        Await.assertThroughout(
            condition = "no stream a volume key can reach moves from $baseline",
            windowMs = windowMs,
        ) { adjustableLevels() == baseline }
    }

    private fun minLevelOf(stream: Int): Int =
        if (android.os.Build.VERSION.SDK_INT >= 28) manager().getStreamMinVolume(stream) else 0

    private fun manager(): AudioManager =
        TestEnvironment.targetContext.getSystemService(AudioManager::class.java)
            ?: throw ConditionTimeoutException("The target context exposes no AudioManager")

    private val KEY_STREAMS = listOf(
        AudioManager.STREAM_MUSIC,
        AudioManager.STREAM_RING,
        AudioManager.STREAM_NOTIFICATION,
    )

    const val STABLE_LEVEL_MS = 1_000L
}
