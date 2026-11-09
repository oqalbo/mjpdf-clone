// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.selection

import android.content.Intent
import android.net.Uri
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.isInternal
import com.gitlab.mudlej.MjPdfReader.suite.core.Await

object SelectionIntents {

    const val ABSENCE_WINDOW_MS = 1_500L

    fun recorded(): List<Intent> = Intents.getIntents()

    fun withAction(action: String): List<Intent> = recorded().filter { intent ->
        intent.action == action && !isInternal().matches(intent)
    }

    fun mark(): Int = recorded().size

    fun withActionSince(action: String, mark: Int): List<Intent> =
        recorded().drop(mark).filter { intent -> intent.action == action && !isInternal().matches(intent) }

    fun awaitSingle(action: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): Intent =
        Await.untilValue(
            condition = "exactly one $action intent is recorded",
            timeoutMs = timeoutMs,
            read = { withAction(action) },
        ) { it.size == 1 }.single()

    fun awaitSingleSince(action: String, mark: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): Intent {
        val intent = Await.untilValue(
            condition = "exactly one new $action intent is recorded",
            timeoutMs = timeoutMs,
            read = { withActionSince(action, mark) },
        ) { it.size == 1 }.single()
        Await.assertThroughout(
            condition = "exactly one new $action intent remains recorded, seen so far: ${describe()}",
            windowMs = ABSENCE_WINDOW_MS,
        ) { withActionSince(action, mark).size == 1 }
        return intent
    }

    fun assertNone(action: String, windowMs: Long = ABSENCE_WINDOW_MS) {
        Await.assertThroughout(
            condition = "no $action intent is recorded, seen so far: ${describe()}",
            windowMs = windowMs,
        ) { withAction(action).isEmpty() }
    }

    fun assertNoneSince(action: String, mark: Int, windowMs: Long = ABSENCE_WINDOW_MS) {
        Await.assertThroughout(
            condition = "no new $action intent is recorded after intent $mark, seen so far: ${describe()}",
            windowMs = windowMs,
        ) { withActionSince(action, mark).isEmpty() }
    }

    fun dataString(intent: Intent): String = intent.data?.toString()
        ?: throw AssertionError("The recorded ${intent.action} intent carries no data uri")

    fun decodedQuery(intent: Intent, parameter: String): String? {
        val data = intent.data ?: return null
        return runCatching { data.getQueryParameter(parameter) }.getOrNull()
            ?: rawParameter(data, parameter)
    }

    fun describe(): String {
        val intents = recorded()
        if (intents.isEmpty()) return "no intent was recorded"
        return intents.joinToString(" | ") { "action=${it.action} data=${it.data} categories=${it.categories}" }
    }

    private fun rawParameter(data: Uri, parameter: String): String? {
        val encoded = data.encodedQuery ?: return null
        val prefix = "$parameter="
        val part = encoded.split('&').firstOrNull { it.startsWith(prefix) } ?: return null
        return Uri.decode(part.removePrefix(prefix))
    }
}
