// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.lists

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.content.Intent
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.rule.IntentsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationCard
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationDump
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.rules.IntentStubs
import com.gitlab.mudlej.MjPdfReader.suite.selection.SelectionShare
import com.gitlab.mudlej.MjPdfReader.suite.selection.ShareQuoteRobot
import com.gitlab.mudlej.MjPdfReader.ui.userhighlights.UserHighlightsActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.espresso.intent.Intents.times

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class ListRoundTripTest : ListsTestCase() {

    @get:Rule
    val intents = IntentsRule()

    @Test
    @TestId("LIST-51")
    fun theShareQuoteFlowIsReachableFromAListRow() {
        SelectionShare.clearCache()
        IntentStubs.stubChooser()

        openReader(ListsFixtures.RICH) { _, _ ->
            val list = openHighlightsAndScan()
            val position = list.positionOfPagePill("Page 6")
            list.scrollTo(position)
            val rowQuote = list.rowPrimaryText(position)
            val target = list.items()[position]
            val group = target.groupKey
            val targetPage = target.pageIndex

            list.tapRow(position)
            Screens.awaitGone(UserHighlightsActivity::class.java, Await.LONG_TIMEOUT_MS)
            awaitReaderSettledOn(targetPage)

            tapFocusedHighlight(target)
            val tapped = AnnotationDump.requireHighlightGroup(targetPage, group)
            assertEquals(
                "the highlight the card opened on carries a different quote from the row that reached it",
                rowQuote.removeSurrounding(ListExport.OPEN_QUOTE, ListExport.CLOSE_QUOTE),
                tapped.quote,
            )

            AnnotationCard.awaitButtonShown(R.id.textSelectionShareButton)
            AnnotationCard.tapButton(R.id.textSelectionShareButton)

            ShareQuoteRobot.awaitShown()
            assertEquals(
                "the book name is taken from the document metadata",
                ListsFixtures.RICH_TITLE,
                ShareQuoteRobot.bookName(),
            )
            assertEquals(
                "the author is taken from the document metadata",
                ListsFixtures.RICH_AUTHOR,
                ShareQuoteRobot.author(),
            )

            ShareQuoteRobot.share()

            SelectionShare.awaitFileCount(1)
            intended(hasAction(Intent.ACTION_CHOOSER), times(1))
        }
    }

    @Test
    @TestId("LIST-56")
    @TestDuration(DurationCategory.SHORT)
    fun copyingAQuoteReachedFromTheListPutsItOnTheClipboard() {
        IntentStubs.clearClipboard()

        openReader(ListsFixtures.RICH) { _, _ ->
            val list = openHighlightsAndScan()
            val position = list.positionOfPagePill("Page 3")
            list.scrollTo(position)
            val rowQuote = list.rowPrimaryText(position)
            val target = list.items()[position]
            val targetPage = target.pageIndex

            list.tapRow(position)
            Screens.awaitGone(UserHighlightsActivity::class.java, Await.LONG_TIMEOUT_MS)
            awaitReaderSettledOn(targetPage)

            tapFocusedHighlight(target)
            AnnotationCard.awaitButtonShown(R.id.textSelectionCopyButton)
            AnnotationCard.tapButton(R.id.textSelectionCopyButton)

            val expected = rowQuote.removeSurrounding(ListExport.OPEN_QUOTE, ListExport.CLOSE_QUOTE)
            Await.untilValue(
                condition = "the clipboard carries the quote of the row the reader came from",
                read = { IntentStubs.clipboardText() },
            ) { it == expected }

            assertEquals(
                "the clip label",
                TestEnvironment.targetContext.getString(R.string.selected_text),
                IntentStubs.clipboardLabel(),
            )
            AnnotationCard.awaitGone()
            assertFalse("the annotation action card survived the copy", AnnotationCard.isShown())
        }
    }
}
