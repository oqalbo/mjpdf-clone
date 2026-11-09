// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.rules

import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class RealOnboarding

class OnboardingSuppressionRule : TestWatcher() {

    override fun starting(description: Description) {
        if (optedOut(description)) return
        SettingsSeeder.suppressOnboarding()
    }

    private fun optedOut(description: Description): Boolean =
        description.getAnnotation(RealOnboarding::class.java) != null ||
            description.testClass?.getAnnotation(RealOnboarding::class.java) != null
}
