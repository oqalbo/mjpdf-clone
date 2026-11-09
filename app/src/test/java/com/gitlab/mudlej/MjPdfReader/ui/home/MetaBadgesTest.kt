// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home

import com.gitlab.mudlej.MjPdfReader.data.entity.ReadingStatus
import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Test

class MetaBadgesTest {
    private val enabled = BadgeToggles(true, true, true, true, true)
    private val formatters = BadgeFormatters({ "$it pages" }, { "$it bytes" }, { "Yesterday" }, { it.name })

    private fun badges(
        style: ListMetaStyle,
        toggles: BadgeToggles = enabled,
        status: ReadingStatus = ReadingStatus.READING,
    ) = metaBadges(style, toggles, 2, 30, true, 10, true, 4096, status, formatters)

    @Ignore("Pending ruling: a stale zero-based page 12 of length 12 displays 13/12 instead of 12/12")
    @Test fun stalePagePositionIsClampedToTheDocumentLength() {
        assertEquals(listOf(Badge("12/12")), metaBadges(
            ListMetaStyle.LIBRARY, BadgeToggles(true, false, false, false, false),
            12, 12, true, 100, true, 0, ReadingStatus.UNSET, formatters,
        ))
    }

    @Test fun unstartedDocumentUsesThePluralPagesFormatter() {
        val lengths = mutableListOf<Int>()
        assertEquals(listOf(Badge("30 pages")), metaBadges(
            ListMetaStyle.LIBRARY, enabled, 0, 30, false, 0, false, 0, ReadingStatus.UNSET,
            formatters.copy(pages = { lengths.add(it); "$it pages" }),
        ))
        assertEquals(listOf(30), lengths)
    }

    @Test fun foldersShowsSizeAndSuppressesProgressLastOpenedAndStatus() {
        assertEquals(listOf(Badge("3/30"), Badge("4096 bytes")), badges(ListMetaStyle.FOLDERS))
    }

    @Test fun libraryShowsProgressAndLastOpenedButNeverSizeOrStatus() {
        assertEquals(listOf(Badge("3/30"), Badge("10%"), Badge("Yesterday")), badges(ListMetaStyle.LIBRARY))
    }

    @Test fun recentStatusIsAccentedAndUnsetStatusIsOmitted() {
        val base = listOf(Badge("3/30"), Badge("10%"), Badge("Yesterday"))
        for (status in ReadingStatus.entries) {
            assertEquals(if (status == ReadingStatus.UNSET) base else base + Badge(status.name, true), badges(ListMetaStyle.RECENT, status = status))
        }
    }

    @Test fun disabledTogglesLeaveEveryStyleEmpty() {
        for (style in ListMetaStyle.entries) {
            assertEquals(emptyList<Badge>(), badges(style, BadgeToggles(false, false, false, false, false)))
        }
    }

    @Test fun unknownLengthSizeAndUnopenedProgressDoNotProduceBadges() {
        for (style in ListMetaStyle.entries) {
            assertEquals(emptyList<Badge>(), metaBadges(style, enabled, 0, 0, false, 0, false, 0, ReadingStatus.UNSET, formatters))
        }
    }
}
