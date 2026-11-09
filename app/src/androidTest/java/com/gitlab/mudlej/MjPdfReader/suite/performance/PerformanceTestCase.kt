// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.performance

import androidx.test.core.app.ActivityScenario
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.RequiresDeviceProfile
import com.gitlab.mudlej.MjPdfReader.suite.rules.RequiresSoftwareRenderer
import com.gitlab.mudlej.MjPdfReader.suite.rules.SoftwareRendererPerformance
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Before
import org.junit.Rule
import java.io.File

@RequiresSoftwareRenderer
@RequiresDeviceProfile("api36-1080x2400-440dpi-3button-en-US-swiftshader")
@SoftwareRendererPerformance
abstract class PerformanceTestCase {

    @get:Rule
    val rules = SuiteRules.standard()

    protected val reader = ReaderRobot()
    protected val perf = PerfReader(reader)

    @Before
    fun seedPerformanceBaseline() {
        SettingsSeeder.suppressOnboarding()
    }

    protected fun fixture(name: String): File =
        PerformanceFixtures.place(name, rules.testDirectory.directory())

    protected fun openReader(name: String, block: (File) -> Unit) {
        val file = fixture(name)
        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(file)).use {
            perf.awaitReady()
            block(file)
        }
    }
}
