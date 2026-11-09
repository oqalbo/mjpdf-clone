// Written by Mudlej. License is GPLv3.
package com.gitlab.mudlej.MjPdfReader.suite.core

import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

object SuiteTiming {
    private data class Frame(var children: Long = 0)
    private data class Totals(var calls: Int = 0, var inclusive: Long = 0, var exclusive: Long = 0)
    private val totals = linkedMapOf<String, Totals>()
    private val frames = ThreadLocal<MutableList<Frame>>()
    @Volatile private var enabled = false

    fun start() {
        enabled = InstrumentationRegistry.getArguments().getString("mjpdfTraceTiming") == "true"
        synchronized(totals) { totals.clear() }
        frames.remove()
    }

    fun <T> measure(name: String, action: () -> T): T {
        if (!enabled) return action()
        val stack = frames.get() ?: mutableListOf<Frame>().also { frames.set(it) }
        val frame = Frame()
        stack.add(frame)
        val started = SystemClock.elapsedRealtimeNanos()
        try {
            return action()
        } finally {
            val elapsed = SystemClock.elapsedRealtimeNanos() - started
            stack.removeAt(stack.lastIndex)
            stack.lastOrNull()?.let { it.children += elapsed }
            synchronized(totals) {
                totals.getOrPut(name) { Totals() }.apply {
                    calls++
                    inclusive += elapsed
                    exclusive += elapsed - frame.children
                }
            }
        }
    }

    fun finish(description: Description) {
        if (!enabled) return
        enabled = false
        val spans = JSONArray()
        synchronized(totals) {
            totals.forEach { (name, value) ->
                spans.put(JSONObject().put("name", name).put("calls", value.calls)
                    .put("inclusiveMs", value.inclusive / 1_000_000.0)
                    .put("exclusiveMs", value.exclusive / 1_000_000.0))
            }
        }
        Log.i("MjPdfTestTiming", JSONObject().put("test", description.displayName)
            .put("exclusiveScope", "thread")
            .put("spans", spans).toString())
        frames.remove()
    }
}

class SuiteTimingRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            SuiteTiming.start()
            try {
                SuiteTiming.measure("test.total") { base.evaluate() }
            } finally {
                runCatching { SuiteTiming.finish(description) }
            }
        }
    }
}
