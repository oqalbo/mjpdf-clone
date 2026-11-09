// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.core

import android.view.accessibility.AccessibilityEvent
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Collections

object AccessibilityToasts {

    private val recorded: MutableList<String> = Collections.synchronizedList(mutableListOf<String>())

    @Volatile
    private var listening = false

    fun start() {
        recorded.clear()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.setOnAccessibilityEventListener { event ->
            if (isToast(event)) {
                event.text.forEach { part -> recorded.add(part.toString()) }
            }
        }
        listening = true
    }

    fun stop() {
        if (!listening) {
            return
        }
        runCatching {
            InstrumentationRegistry.getInstrumentation().uiAutomation.setOnAccessibilityEventListener(null)
        }
        listening = false
    }

    fun texts(): List<String> = synchronized(recorded) { recorded.toList() }

    private fun isToast(event: AccessibilityEvent): Boolean =
        event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_ANNOUNCEMENT
}
