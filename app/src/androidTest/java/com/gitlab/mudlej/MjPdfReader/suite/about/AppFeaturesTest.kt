// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.about

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.content.Intent
import android.text.Spanned
import android.text.style.BulletSpan
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.runner.lifecycle.Stage
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.ActivityWatcher
import com.gitlab.mudlej.MjPdfReader.suite.core.Rotation
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.AboutRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.AppFeaturesRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.FeatureTopicRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.about.AppFeaturesActivity
import com.gitlab.mudlej.MjPdfReader.ui.about.FeatureTopic
import com.gitlab.mudlej.MjPdfReader.ui.about.FeatureTopicActivity
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class AppFeaturesTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val home = HomeRobot()
    private val about = AboutRobot()
    private val features = AppFeaturesRobot()
    private val topic = FeatureTopicRobot()

    @Test
    @TestId("ABOUT-44")
    fun appFeaturesListsAllSevenTopicsWithSubtitles() {
        openAppFeatures()

        assertEquals("the App Features action bar title", string(R.string.features_title), Screens.titleOfResumed())
        assertEquals("one row per FeatureTopic entry", FeatureTopic.entries.size, features.topicCount())
        assertEquals("the seven topic titles in enum order", topicTitles(), features.topicTitles())

        features.topicSubtitles().forEachIndexed { index, (subtitle, visibility) ->
            assertEquals("topic row $index must show its subtitle", View.VISIBLE, visibility)
            assertTrue("topic row $index has an empty subtitle", subtitle.isNotBlank())
        }
    }

    @Test
    @TestId("ABOUT-45")
    @TestDuration(DurationCategory.NORMAL)
    fun everyFeatureTopicOpensWithItsCompleteEntryList() {
        openAppFeatures()

        for (entry in FeatureTopic.entries) {
            val title = string(entry.titleRes)
            features.openTopic(title)
            Screens.awaitResumed(FeatureTopicActivity::class.java)
            topic.awaitShown()

            assertEquals("the entry count of $entry", entry.entries.size, topic.entryCount())
            assertEquals("the detail page title of $entry", title, Screens.titleOfResumed())

            topic.entries().forEachIndexed { index, (rowTitle, rowBody) ->
                assertTrue("$entry entry $index has an empty title", rowTitle.isNotBlank())
                assertTrue("$entry entry $index has an empty body", rowBody.isNotBlank())
            }

            topic.pressBack()
            Screens.awaitTopIs(AppFeaturesActivity::class.java)
            features.awaitShown()
            assertEquals("the topic list must survive the return", FeatureTopic.entries.size, features.topicCount())
        }

        assertEquals(
            "the seven topics must hold sixty nine entries in total",
            69,
            FeatureTopic.entries.sumOf { it.entries.size },
        )
    }

    @Test
    @TestId("ABOUT-46")
    @TestDuration(DurationCategory.NORMAL)
    fun aFeatureTopicBodyRendersBulletsAndALeadLineDoesNotGetOne() {
        openAppFeatures()
        features.openTopic(string(FeatureTopic.HOME.titleRes))
        Screens.awaitResumed(FeatureTopicActivity::class.java)
        topic.awaitShown()

        val options = topic.bodyOf(string(R.string.features_home_options_title))
        val spanned = options as? Spanned
            ?: throw AssertionError("The multi line options body must be a Spanned, it was ${options::class.java.name}")
        val bullets = spanned.getSpans(0, spanned.length, BulletSpan::class.java)

        assertEquals("the six line options body must carry five bullets", 5, bullets.size)

        val leadLineEnd = spanned.toString().indexOf('\n')
        assertTrue("the options body must be multi line", leadLineEnd > 0)
        val bulletedLead = bullets.filter { spanned.getSpanStart(it) < leadLineEnd }
        assertTrue(
            "the lead line ends with a colon so it must not be bulleted, ${bulletedLead.size} bullets cover it",
            bulletedLead.isEmpty(),
        )

        assertEquals(
            "a single line body must be returned raw and carry no bullets",
            0,
            bulletCountOf(topic.bodyOf(string(R.string.features_home_tabs_title))),
        )

        topic.pressBack()
        Screens.awaitTopIs(AppFeaturesActivity::class.java)
        features.awaitShown()

        features.openTopic(string(FeatureTopic.READING.titleRes))
        Screens.awaitResumed(FeatureTopicActivity::class.java)
        topic.awaitShown()

        assertEquals(
            "a two line body whose first line has no colon must be bulleted twice",
            2,
            bulletCountOf(topic.bodyOf(string(R.string.features_reading_fit_title))),
        )
    }

    @Test
    @TestId("ABOUT-47")
    fun anInAppIntentWithATopicExtraOpensThatTopicDirectly() {
        home.launchFromLauncher()
        home.awaitRendered()

        startFeatureTopic(FeatureTopic.PRIVACY.name)
        Screens.awaitResumed(FeatureTopicActivity::class.java)
        topic.awaitShown()

        assertEquals(
            "the detail page title",
            string(R.string.features_topic_privacy),
            Screens.titleOfResumed(),
        )
        assertEquals("the privacy topic entry count", 9, topic.entryCount())
        assertEquals(
            "the first privacy entry title",
            string(R.string.features_privacy_local_title),
            topic.entries().first().first,
        )
    }

    @Test
    @TestId("ABOUT-48")
    fun aFeatureTopicWithAMissingExtraFinishesImmediately() {
        home.launchFromLauncher()
        home.awaitRendered()

        ActivityWatcher().use { watcher ->
            startFeatureTopic(null)
            watcher.awaitReached(FeatureTopicActivity::class.java, Stage.DESTROYED)

            assertTrue(
                "the activity must close itself rather than being torn down some other way",
                watcher.wasFinishingWhenDestroyed(FeatureTopicActivity::class.java),
            )
        }

        Screens.awaitTopIs(HomeActivity::class.java)
        assertFalse("no feature entry row may ever be displayed", topic.isAnyEntryRowDisplayed())
    }

    @Test
    @TestId("ABOUT-49")
    fun aFeatureTopicWithAnUnknownTopicNameFinishesImmediately() {
        home.launchFromLauncher()
        home.awaitRendered()

        for (name in listOf("NOT_A_TOPIC", "home")) {
            ActivityWatcher().use { watcher ->
                startFeatureTopic(name)
                watcher.awaitReached(FeatureTopicActivity::class.java, Stage.DESTROYED)

                assertTrue(
                    "the activity must close itself for the topic name \"$name\"",
                    watcher.wasFinishingWhenDestroyed(FeatureTopicActivity::class.java),
                )
            }
            Screens.awaitTopIs(HomeActivity::class.java)
        }

    }

    @Test
    @TestId("ABOUT-50")
    @TestDuration(DurationCategory.NORMAL)
    fun rotationOnAFeatureTopicKeepsTheTopicAndDoesNotDuplicateEntries() {
        openAppFeatures()
        features.openTopic(string(FeatureTopic.READING.titleRes))
        Screens.awaitResumed(FeatureTopicActivity::class.java)
        topic.awaitShown()

        assertReadingTopicShape()

        Rotation.toLandscape(FeatureTopicActivity::class.java)
        topic.awaitShown()
        assertReadingTopicShape()

        Rotation.toPortrait(FeatureTopicActivity::class.java)
        topic.awaitShown()
        assertReadingTopicShape()
    }

    private fun assertReadingTopicShape() {
        assertEquals("the reading topic entry count", 17, topic.entryCount())
        assertEquals(
            "the reading topic title",
            string(R.string.features_topic_reading),
            Screens.titleOfResumed(),
        )
        assertTrue(
            "the activity must not finish, the topic extra has to survive recreation",
            Screens.isInTask(FeatureTopicActivity::class.java),
        )
    }

    private fun openAppFeatures() {
        about.openFromHome(home)
        about.openRow(R.id.appFeaturesRow)
        Screens.awaitResumed(AppFeaturesActivity::class.java)
        features.awaitShown()
    }

    private fun startFeatureTopic(topicName: String?) {
        val intent = Intent(TestEnvironment.targetContext, FeatureTopicActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (topicName != null) {
            intent.putExtra(FeatureTopicActivity.EXTRA_TOPIC, topicName)
        }
        TestEnvironment.targetContext.startActivity(intent)
        TestEnvironment.uiDevice.waitForIdle()
    }

    private fun bulletCountOf(body: CharSequence): Int {
        val spanned = body as? Spanned ?: return 0
        return spanned.getSpans(0, spanned.length, BulletSpan::class.java).size
    }

    private fun topicTitles(): List<String> = FeatureTopic.entries.map { string(it.titleRes) }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)
}
