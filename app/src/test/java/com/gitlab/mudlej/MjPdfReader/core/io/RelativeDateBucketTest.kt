// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.core.io

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class RelativeDateBucketTest {
    private val today = LocalDate.of(2024, 3, 1)

    @Test fun futureAndPresentDatesUseTodayAndOneDayUsesYesterday() {
        assertEquals(RelativeDateUnit.TODAY to 0, relativeDateBucket(today.plusDays(2), today))
        assertEquals(RelativeDateUnit.TODAY to 0, relativeDateBucket(today, today))
        assertEquals(RelativeDateUnit.YESTERDAY to 1, relativeDateBucket(today.minusDays(1), today))
    }

    @Test fun dayWeekMonthAndYearBoundariesChooseTheExpectedUnit() {
        for ((days, expected) in listOf(
            6L to (RelativeDateUnit.DAYS to 6), 7L to (RelativeDateUnit.WEEKS to 1),
            29L to (RelativeDateUnit.WEEKS to 4), 30L to (RelativeDateUnit.MONTHS to 1),
            364L to (RelativeDateUnit.MONTHS to 11), 365L to (RelativeDateUnit.YEARS to 1))) {
            assertEquals("$days days", expected, relativeDateBucket(today.minusDays(days), today))
        }
    }

    @Test fun leapYearJanuaryEndToMarchStartCountsAsOneMonth() {
        assertEquals(RelativeDateUnit.MONTHS to 1, relativeDateBucket(LocalDate.of(2024, 1, 31), today))
        assertEquals(RelativeDateUnit.MONTHS to 1,
            relativeDateBucket(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)))
    }
}
