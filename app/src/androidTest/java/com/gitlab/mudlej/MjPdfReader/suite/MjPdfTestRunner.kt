// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.runner.AndroidJUnitRunner

class MjPdfTestRunner : AndroidJUnitRunner() {

    private lateinit var arguments: Bundle

    override fun onCreate(arguments: Bundle) {
        this.arguments = Bundle(arguments)
        super.onCreate(arguments)
    }

    override fun onStart() {
        cancelInjectedTouch()
        if (shouldGrantLegacyStorage()) {
            grantLegacyStorage()
        }
        super.onStart()
    }

    private fun cancelInjectedTouch() {
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
        try {
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            uiAutomation.injectInputEvent(event, true)
        } finally {
            event.recycle()
        }
    }

    private fun shouldGrantLegacyStorage(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R &&
            arguments.getString(SCENARIO_ARGUMENT).isNullOrBlank() &&
            arguments.getString(STORAGE_ACCESS_ARGUMENT) != STORAGE_ACCESS_DENIED

    private fun grantLegacyStorage() {
        LEGACY_STORAGE_PERMISSIONS.forEach { permission ->
            val descriptor = uiAutomation.executeShellCommand(
                "pm grant $TARGET_PACKAGE $permission"
            )
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
        val deadline = SystemClock.uptimeMillis() + GRANT_TIMEOUT_MS
        while (!hasLegacyStorage() && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(GRANT_POLL_MS)
        }
        check(hasLegacyStorage()) {
            "Could not restore legacy shared-storage permissions after Orchestrator cleared $TARGET_PACKAGE"
        }
    }

    private fun hasLegacyStorage(): Boolean = LEGACY_STORAGE_PERMISSIONS.all { permission ->
        targetContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        const val TARGET_PACKAGE = "com.gitlab.mudlej.MjPdfReader"
        const val SCENARIO_ARGUMENT = "mjpdfScenario"
        const val STORAGE_ACCESS_ARGUMENT = "storageAccess"
        const val STORAGE_ACCESS_DENIED = "denied"
        const val GRANT_TIMEOUT_MS = 5_000L
        const val GRANT_POLL_MS = 50L
        val LEGACY_STORAGE_PERMISSIONS = listOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        )
    }
}
