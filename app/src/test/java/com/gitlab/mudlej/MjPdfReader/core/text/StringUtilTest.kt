// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.core.text

import com.gitlab.mudlej.MjPdfReader.core.text.StringUtil.formatEnumToTitle
import com.gitlab.mudlej.MjPdfReader.core.text.StringUtil.formatTitleToEnum
import com.gitlab.mudlej.MjPdfReader.data.entity.ReadingStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class StringUtilTest {

    private lateinit var originalLocale: Locale

    @Before
    fun saveLocale() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.ROOT)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun underscoresBecomeTitleWordSeparators() {
        assertEquals("To Read", "TO_READ".formatEnumToTitle())
    }

    @Test
    fun everyReadingStatusRoundTripsThroughItsTitle() {
        for (status in ReadingStatus.values()) {
            val title = status.name.formatEnumToTitle()
            assertEquals(status.name, status.name, title.formatTitleToEnum())
        }
    }

    @Test
    fun turkishLocaleDoesNotChangeTitleOrEnumCasing() {
        Locale.setDefault(Locale.forLanguageTag("tr-TR"))
        assertEquals("To Read", "TO_READ".formatEnumToTitle())
        assertEquals("Reading", "READING".formatEnumToTitle())
        assertEquals("READING", "Reading".formatTitleToEnum())
    }
}
