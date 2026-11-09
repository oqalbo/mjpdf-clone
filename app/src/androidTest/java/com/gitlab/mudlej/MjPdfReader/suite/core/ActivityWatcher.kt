// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.core

import android.app.Activity
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import java.io.Closeable
import java.util.Collections

class ActivityWatcher : Closeable {

    private val stages = Collections.synchronizedMap(LinkedHashMap<String, MutableSet<Stage>>())
    private val destroyedWhileFinishing = Collections.synchronizedSet(LinkedHashSet<String>())

    private val callback = ActivityLifecycleCallback { activity, stage ->
        val name = activity.javaClass.name
        synchronized(stages) { stages.getOrPut(name) { LinkedHashSet() }.add(stage) }
        if (stage == Stage.DESTROYED && activity.isFinishing) {
            destroyedWhileFinishing.add(name)
        }
    }

    init {
        ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(callback)
    }

    fun reached(type: Class<out Activity>, stage: Stage): Boolean =
        synchronized(stages) { stages[type.name]?.contains(stage) == true }

    fun wasFinishingWhenDestroyed(type: Class<out Activity>): Boolean =
        type.name in destroyedWhileFinishing

    fun awaitReached(type: Class<out Activity>, stage: Stage, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("${type.simpleName} reaches $stage, observed ${observed()}", timeoutMs) { reached(type, stage) }
    }

    fun assertNeverReached(type: Class<out Activity>, stage: Stage) {
        if (reached(type, stage)) {
            throw AssertionError("${type.name} reached $stage while it must not have, observed ${observed()}")
        }
    }

    fun observed(): String = synchronized(stages) {
        stages.entries.joinToString(", ") { "${it.key.substringAfterLast('.')}=${it.value}" }
    }

    override fun close() {
        ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(callback)
    }
}
