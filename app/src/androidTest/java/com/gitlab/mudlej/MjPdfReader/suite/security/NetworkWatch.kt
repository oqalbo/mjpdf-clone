// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.security

import android.net.TrafficStats
import android.os.Process
import com.gitlab.mudlej.MjPdfReader.suite.core.Await

data class TrafficSample(val txBytes: Long, val rxBytes: Long) {
    val isMeasured: Boolean
        get() = txBytes != TrafficStats.UNSUPPORTED.toLong() && rxBytes != TrafficStats.UNSUPPORTED.toLong()

    override fun toString(): String = "tx $txBytes bytes, rx $rxBytes bytes"
}

object NetworkWatch {
    fun sample(): TrafficSample {
        val uid = Process.myUid()
        return TrafficSample(TrafficStats.getUidTxBytes(uid), TrafficStats.getUidRxBytes(uid))
    }

    fun assertUnchangedSince(before: TrafficSample, what: String) {
        val after = sample()
        if (!before.isMeasured || !after.isMeasured) {
            return
        }
        if (after.txBytes != before.txBytes || after.rxBytes != before.rxBytes) {
            throw AssertionError(
                "$what moved the process traffic counters. Before: $before. After: $after. " +
                    "If the request log of the local server is empty, check for an ACRA upload before " +
                    "concluding that a document caused this."
            )
        }
    }

    fun holdUnchanged(before: TrafficSample, what: String, windowMs: Long = SecurityReader.INERT_WINDOW_MS) {
        Await.assertThroughout(
            condition = "$what leaves the process traffic counters at $before",
            windowMs = windowMs,
        ) {
            val after = sample()
            !before.isMeasured || !after.isMeasured ||
                (after.txBytes == before.txBytes && after.rxBytes == before.rxBytes)
        }
    }
}
