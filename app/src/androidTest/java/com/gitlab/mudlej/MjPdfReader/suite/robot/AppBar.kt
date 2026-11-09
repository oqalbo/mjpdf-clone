// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.robot

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment

object AppBar {

    fun upDescription(): String =
        TestEnvironment.targetContext.getString(androidx.appcompat.R.string.abc_action_bar_up_description)

    fun pressUp() {
        onView(withContentDescription(upDescription())).perform(click())
    }
}
