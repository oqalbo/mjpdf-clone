// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.rules

import com.gitlab.mudlej.MjPdfReader.suite.core.SuiteTimingRule
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class SuiteRules(
    val sharedStorage: SharedStorageRule = SharedStorageRule(),
    val testDirectory: TestDirectoryRule = TestDirectoryRule(),
    val onboarding: OnboardingSuppressionRule = OnboardingSuppressionRule(),
    val failureArtifacts: FailureArtifactRule = FailureArtifactRule(),
    val orientation: OrientationRule = OrientationRule(),
    val crashGate: CrashGateRule = CrashGateRule(),
    val toasts: ToastRule = ToastRule(),
) : TestRule {

    private val chain: RuleChain = RuleChain
        .outerRule(SuiteTimingRule())
        .around(sharedStorage)
        .around(testDirectory)
        .around(onboarding)
        .around(orientation)
        .around(failureArtifacts)
        .around(crashGate)
        .around(toasts)

    override fun apply(base: Statement, description: Description): Statement =
        chain.apply(base, description)

    companion object {
        fun standard() = SuiteRules()

        fun withoutCrashGate() = SuiteRules(crashGate = CrashGateRule(enabled = false))
    }
}
