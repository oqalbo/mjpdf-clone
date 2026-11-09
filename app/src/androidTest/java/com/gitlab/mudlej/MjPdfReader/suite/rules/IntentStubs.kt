// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.rules

import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.intent.matcher.ComponentNameMatchers.hasPackageName
import androidx.test.espresso.intent.matcher.IntentMatchers.isInternal
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.not

object IntentStubs {

    fun stubChooser() {
        intending(hasAction(Intent.ACTION_CHOOSER)).respondWith(cancelledResult())
    }

    fun stubEveryOutgoingIntent() {
        intending(allOf(
            not(isInternal()),
            not(hasComponent(hasPackageName(TestEnvironment.testContext.packageName))),
        )).respondWith(cancelledResult())
    }

    fun clearClipboard() {
        TestEnvironment.onMainThread {
            val clipboard = TestEnvironment.targetContext
                .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboard.clearPrimaryClip()
            } else {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
    }

    fun clipboardText(): String? = TestEnvironment.onMainThread {
        val clipboard = TestEnvironment.targetContext
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
    }

    fun clipboardLabel(): String? = TestEnvironment.onMainThread {
        val clipboard = TestEnvironment.targetContext
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.primaryClipDescription?.label?.toString()
    }

    fun stubOkFor(action: String) {
        intending(allOf(hasAction(action), not(isInternal()))).respondWith(okResult())
    }

    fun stubUriResultFor(action: String, uri: Uri) {
        val data = Intent()
            .setData(uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            .addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        intending(hasAction(action)).respondWith(ActivityResult(Activity.RESULT_OK, data))
    }

    private fun cancelledResult() = ActivityResult(Activity.RESULT_CANCELED, null)

    private fun okResult() = ActivityResult(Activity.RESULT_OK, null)
}
