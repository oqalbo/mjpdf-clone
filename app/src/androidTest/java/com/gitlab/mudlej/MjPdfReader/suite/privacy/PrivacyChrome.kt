// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.privacy

import android.app.Activity
import android.util.TypedValue
import androidx.core.content.ContextCompat
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment

object PrivacyChrome {

    fun incognitoSurfaceColor(): Int =
        ContextCompat.getColor(TestEnvironment.targetContext, R.color.incognito_surface)

    fun surfaceColorOf(activity: Activity): Int = TestEnvironment.onMainThread {
        val value = TypedValue()
        val resolved = activity.theme.resolveAttribute(
            com.google.android.material.R.attr.colorSurface,
            value,
            true,
        )
        if (!resolved) {
            throw ConditionTimeoutException(
                "${activity.javaClass.simpleName} resolves no colorSurface, so its chrome cannot be read"
            )
        }
        if (value.resourceId != 0) ContextCompat.getColor(activity, value.resourceId) else value.data
    }

    fun isIncognitoChrome(activity: Activity): Boolean =
        surfaceColorOf(activity) == incognitoSurfaceColor()

    fun describe(activity: Activity): String =
        "${activity.javaClass.simpleName} paints colorSurface as ${hex(surfaceColorOf(activity))} " +
            "against the incognito surface ${hex(incognitoSurfaceColor())}"

    private fun hex(colour: Int): String = String.format("#%08X", colour)
}
