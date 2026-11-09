// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.view

import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import kotlin.math.abs

object ViewMotion {

    fun awaitMonotonic(
        condition: String,
        steps: Int,
        timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS,
        increasing: Boolean,
        tolerance: Float = 0f,
        read: () -> Float,
    ): List<Float> {
        val collected = mutableListOf<Float>()
        Await.until("$condition, over $steps consecutive samples", timeoutMs) {
            val value = read()
            val last = collected.lastOrNull()
            when {
                last == null -> collected.add(value)
                increasing && value > last -> collected.add(value)
                !increasing && value < last -> collected.add(value)
                increasing && value < last - tolerance -> throw ConditionTimeoutException(
                    "$condition: the value moved backwards from $last to $value, samples so far $collected"
                )
                !increasing && value > last + tolerance -> throw ConditionTimeoutException(
                    "$condition: the value moved forwards from $last to $value, samples so far $collected"
                )
            }
            collected.size > steps
        }
        return collected
    }

    fun assertStable(
        condition: String,
        windowMs: Long,
        tolerance: Float = 0f,
        read: () -> Float,
    ): Float {
        val start = read()
        Await.assertThroughout(
            condition = "$condition, holding at $start",
            windowMs = windowMs,
        ) { abs(read() - start) <= tolerance }
        return start
    }
}
