// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.core.io

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class DateUtilPdfDateTest {

    private lateinit var originalTimeZone: TimeZone
    private lateinit var originalLocale: Locale

    @Before
    fun useUtc() {
        originalTimeZone = TimeZone.getDefault()
        originalLocale = Locale.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Locale.setDefault(Locale.ROOT)
    }

    @After
    fun restoreDefaults() {
        TimeZone.setDefault(originalTimeZone)
        Locale.setDefault(originalLocale)
    }

    @Test
    fun yearOnlyDefaultsToJanuaryFirst() {
        assertEquals("2024-01-01", convertDateString("D:2024"))
    }

    @Test
    fun dayBeyondFebruaryClampsToLeapDay() {
        assertEquals("2024-02-29", convertDateString("D:20240231"))
    }

    @Test
    fun positiveOffsetSubtractsHoursAndMinutes() {
        assertEquals("2024-01-15 05:00", convertDateString("D:20240115103000+05'30'"))
    }

    @Test
    fun zuluOffsetKeepsTheUtcTime() {
        assertEquals("2024-01-15 10:30", convertDateString("D:20240115103000Z"))
    }

    @Test
    fun negativeOffsetMovesTheTimeIntoTheNextUtcDay() {
        assertEquals("2024-01-16 04:30", convertDateString("D:20240115203000-08'00'"))
    }

    @Test
    fun invalidOffsetIsIgnoredWithoutRejectingTheDate() {
        assertEquals("2024-01-15 10:30", convertDateString("D:20240115103000+25'00'"))
    }

    @Test
    fun offsetsWithoutATimePartLeaveTheDateUnchanged() {
        assertEquals("2024-01-15", convertDateString("D:20240115Z"))
        assertEquals("2024-01-15", convertDateString("D:20240115+05'30'"))
    }

    @Test
    fun monthZeroClampsToJanuary() {
        assertEquals("2024-01-15", convertDateString("D:20240015"))
    }

    @Test
    fun secondSixtyClampsWithoutRollingIntoTheNextMinute() {
        assertEquals("2024-01-15 10:30", convertDateString("D:20240115103060"))
    }

    @Test
    fun incompleteYearsAndNonDatesAreRejected() {
        assertNull(convertDateString("D:202"))
        assertNull(convertDateString("garbage"))
    }

    @Test
    fun arabicEgyptLocaleStillProducesAsciiDigits() {
        Locale.setDefault(Locale.forLanguageTag("ar-EG"))
        assertEquals("2024-01-15", convertDateString("D:20240115"))
        assertEquals("2024-01-15 05:00", convertDateString("D:20240115103000+05'30'"))
    }
}
