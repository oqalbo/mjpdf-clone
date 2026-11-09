// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.core

import android.app.Activity
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.assertEquals

object Screens {

    private val liveStages = listOf(Stage.CREATED, Stage.STARTED, Stage.RESUMED, Stage.PAUSED, Stage.STOPPED)

    fun resumedName(): String? = TestEnvironment.resumedActivity()?.javaClass?.name

    fun awaitResumed(type: Class<out Activity>, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): Activity =
        Await.untilNotNull("${type.simpleName} is resumed", timeoutMs) {
            TestEnvironment.resumedActivityOfType(type)
        }

    fun awaitTopIs(type: Class<out Activity>, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            "${type.simpleName} is the resumed activity",
            timeoutMs,
            read = { resumedName() },
        ) { it == type.name }
    }

    fun assertTopIs(type: Class<out Activity>) {
        assertEquals("the resumed activity", type.name, resumedName())
    }

    fun isInTask(type: Class<out Activity>): Boolean = TestEnvironment.onMainThread {
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        liveStages.any { stage -> monitor.getActivitiesInStage(stage).any { type.isInstance(it) } }
    }

    fun <T : Activity> liveActivitiesOfType(type: Class<T>): List<T> = TestEnvironment.onMainThread {
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        liveStages
            .flatMap { stage -> monitor.getActivitiesInStage(stage) }
            .filter { type.isInstance(it) }
            .mapNotNull(type::cast)
            .distinct()
    }

    fun <T : Activity> awaitReplacement(
        previous: T,
        type: Class<T>,
        timeoutMs: Long = Await.LONG_TIMEOUT_MS,
    ): T = Await.untilNotNull(
        "${type.simpleName} instance ${System.identityHashCode(previous)} is destroyed and replaced",
        timeoutMs,
    ) {
        liveActivitiesOfType(type).firstOrNull { it !== previous }
            ?.takeIf { previous.isDestroyed }
    }

    fun awaitGone(type: Class<out Activity>, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("${type.simpleName} is no longer in the task", timeoutMs) { !isInTask(type) }
    }

    fun finishAppTask(stages: Collection<Stage> = Stage.entries) {
        TestEnvironment.onMainThread {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            stages
                .flatMap { monitor.getActivitiesInStage(it) }
                .distinct()
                .forEach { runCatching { it.finishAndRemoveTask() } }
        }
        TestEnvironment.waitForIdleSync()
    }

    fun isInStage(type: Class<out Activity>, stage: Stage): Boolean = TestEnvironment.onMainThread {
        ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage).any { type.isInstance(it) }
    }

    fun awaitStage(type: Class<out Activity>, stage: Stage, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("${type.simpleName} reaches ${stage.name}", timeoutMs) { isInStage(type, stage) }
    }

    fun awaitStage(activity: Activity, stage: Stage, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("${activity.javaClass.simpleName} instance reaches ${stage.name}", timeoutMs) {
            TestEnvironment.onMainThread {
                ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(stage)
                    .any { it === activity }
            }
        }
    }

    fun titleOfResumed(): String? = TestEnvironment.onMainThread {
        TestEnvironment.resumedActivity()?.title?.toString()
    }

    fun awaitTitle(expected: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            "the resumed activity is titled \"$expected\"",
            timeoutMs,
            read = { titleOfResumed() },
        ) { it == expected }
    }
}
