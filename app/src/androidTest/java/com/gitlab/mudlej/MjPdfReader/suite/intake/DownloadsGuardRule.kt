// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.intake

import androidx.lifecycle.lifecycleScope
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File
import java.lang.ref.WeakReference

class DownloadsGuardRule : TestWatcher() {

    private var before: Set<String> = emptySet()
    private var hadAccessAtStart = false
    private val readers = mutableListOf<Pair<WeakReference<MainActivity>, Job>>()
    private val lifecycle = ActivityLifecycleCallback { activity, stage ->
        if (activity is MainActivity && stage == Stage.CREATED) {
            activity.lifecycleScope.coroutineContext[Job]?.let { job ->
                readers += WeakReference(activity) to job
            }
        }
    }

    override fun starting(description: Description) {
        hadAccessAtStart = Fixtures.hasAllFilesAccess()
        before = namesInDownloads()
        TestEnvironment.onMainThread {
            ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(lifecycle)
        }
    }

    override fun finished(description: Description) {
        TestEnvironment.onMainThread {
            ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(lifecycle)
            readers.forEach { (reference, _) ->
                reference.get()?.takeIf { !it.isDestroyed }?.finishAndRemoveTask()
            }
        }
        if (readers.isNotEmpty()) {
            Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
            runBlocking {
                withTimeout(Await.LONG_TIMEOUT_MS) { readers.map { it.second }.joinAll() }
            }
            readers.clear()
        }
        IntakeReader.deleteDurableCopies()
        if (!hadAccessAtStart || !Fixtures.hasAllFilesAccess()) {
            return
        }
        (namesInDownloads() - before).forEach { name ->
            runCatching { Fixtures.delete(File(IntakeFixtures.seededRoot, name)) }
        }
    }

    private fun namesInDownloads(): Set<String> =
        IntakeFixtures.seededRoot.list()?.toSet().orEmpty()
}
