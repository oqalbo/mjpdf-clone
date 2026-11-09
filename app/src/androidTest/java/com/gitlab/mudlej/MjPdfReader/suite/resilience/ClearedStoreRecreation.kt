// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.resilience

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import java.util.concurrent.atomic.AtomicBoolean

object ClearedStoreRecreation {

    fun <T : Activity> recreate(scenario: ActivityScenario<T>, type: Class<out Activity>) {
        val cleared = armOneShotStoreClear(type)
        scenario.recreate()
        requireFired(cleared, type)
    }

    private fun armOneShotStoreClear(type: Class<out Activity>): AtomicBoolean {
        val fired = AtomicBoolean(false)
        val application = TestEnvironment.targetContext.applicationContext as Application
        val callbacks = object : Application.ActivityLifecycleCallbacks {

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
                if (!type.isInstance(activity)) return
                val owner = activity as? ComponentActivity ?: return
                owner.viewModelStore.clear()
                fired.set(true)
                application.unregisterActivityLifecycleCallbacks(this)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        }
        application.registerActivityLifecycleCallbacks(callbacks)
        return fired
    }

    private fun requireFired(fired: AtomicBoolean, type: Class<out Activity>) {
        if (fired.get()) return
        throw AssertionError(
            "The ViewModelStore of ${type.simpleName} was never cleared, so this recreation kept its view " +
                "models and proves only that a configuration change survives. The clear is driven from " +
                "Application.onActivitySaveInstanceState, which runs after the saved state registry has " +
                "already written the view models' state into the outgoing bundle and before the store is " +
                "retained for the new instance. A run reaching this message means that ordering no longer " +
                "holds and every restore assertion behind it is worthless until it is fixed."
        )
    }
}
