// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.core

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Looper
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.UiDevice

object TestEnvironment {

    val instrumentation: Instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    val targetContext: Context
        get() = instrumentation.targetContext

    val testContext: Context
        get() = instrumentation.context

    val targetPackage: String
        get() = targetContext.packageName

    val uiDevice: UiDevice
        get() = UiDevice.getInstance(instrumentation)

    val defaultPreferences: SharedPreferences
        get() = PreferenceManager.getDefaultSharedPreferences(targetContext)

    fun <T> onMainThread(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        var result: T? = null
        var thrown: Throwable? = null
        instrumentation.runOnMainSync {
            try {
                result = block()
            } catch (error: Throwable) {
                thrown = error
            }
        }
        thrown?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    fun resumedActivity(): Activity? = onMainThread {
        ActivityLifecycleMonitorRegistry.getInstance()
            .getActivitiesInStage(Stage.RESUMED)
            .firstOrNull()
    }

    fun resumedActivityOfType(type: Class<out Activity>): Activity? = onMainThread {
        ActivityLifecycleMonitorRegistry.getInstance()
            .getActivitiesInStage(Stage.RESUMED)
            .firstOrNull { type.isInstance(it) }
    }

    fun targetString(@StringRes resId: Int): String = targetContext.getString(resId)

    fun appString(@StringRes resId: Int): String =
        appStringContext().getString(resId)

    fun appString(@StringRes resId: Int, vararg formatArgs: Any): String =
        appStringContext().getString(resId, *formatArgs)

    private fun appStringContext(): Context {
        resumedActivity()?.let { return it }
        val locale = AppCompatDelegate.getApplicationLocales()[0] ?: return targetContext
        val configuration = Configuration(targetContext.resources.configuration)
        configuration.setLocale(locale)
        return targetContext.createConfigurationContext(configuration)
    }

    fun waitForIdleSync() {
        SuiteTiming.measure("ui.mainIdle") { instrumentation.waitForIdleSync() }
    }
}
