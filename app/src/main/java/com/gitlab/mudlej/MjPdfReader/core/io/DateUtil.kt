// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.core.io

import android.content.Context
import com.gitlab.mudlej.MjPdfReader.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

val appDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
val appDateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private val pdfDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("'D:'yyyyMMddHHmmss")

fun pdfDateNow(): String = LocalDateTime.now().format(pdfDateFormatter)

fun formatRelativeDate(context: Context, dateTime: LocalDateTime): String {
    val (unit, count) = relativeDateBucket(dateTime.toLocalDate(), LocalDate.now())
    return when (unit) {
        RelativeDateUnit.TODAY -> context.getString(R.string.home_relative_today)
        RelativeDateUnit.YESTERDAY -> context.getString(R.string.home_relative_yesterday)
        RelativeDateUnit.DAYS -> context.getString(R.string.home_relative_days_ago, count.toLong())
        RelativeDateUnit.WEEKS -> context.resources.getQuantityString(R.plurals.home_relative_weeks_ago, count, count)
        RelativeDateUnit.MONTHS -> context.resources.getQuantityString(R.plurals.home_relative_months_ago, count, count)
        RelativeDateUnit.YEARS -> context.resources.getQuantityString(R.plurals.home_relative_years_ago, count, count)
    }
}

fun convertDateString(input: String?): String? {
    if (input.isNullOrBlank()) return null

    return try {
        val value = input.trim().removePrefix("D:")
        val digits = value.takeWhile { it.isDigit() }
        if (digits.length < 4) return null

        fun component(start: Int, end: Int, default: Int): Int =
            if (digits.length >= end) digits.substring(start, end).toInt() else default

        val year = digits.substring(0, 4).toInt()
        val month = component(4, 6, 1).coerceIn(1, 12)
        val day = component(6, 8, 1).coerceIn(1, YearMonth.of(year, month).lengthOfMonth())
        val hour = component(8, 10, 0).coerceIn(0, 23)
        val minute = component(10, 12, 0).coerceIn(0, 59)
        val second = component(12, 14, 0).coerceIn(0, 59)
        val hasTime = digits.length >= 10

        val dateTime = LocalDateTime.of(year, month, day, hour, minute, second)
        val offset = parsePdfUtcOffset(value.substring(digits.length))
        val localized = if (offset != null && hasTime) {
            dateTime.atOffset(offset).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
        } else {
            dateTime
        }

        val formatter = if (hasTime) appDateTimeFormatter else appDateFormatter
        localized.format(formatter)
    }
    catch (throwable: Throwable) {
        null
    }
}

private fun parsePdfUtcOffset(zone: String): ZoneOffset? {
    val trimmed = zone.trim()
    if (trimmed.isEmpty()) return null
    if (trimmed.startsWith("Z")) return ZoneOffset.UTC

    val sign = when (trimmed.first()) {
        '+' -> 1
        '-' -> -1
        else -> return null
    }
    val numbers = trimmed.drop(1).filter { it.isDigit() }
    if (numbers.length < 2) return null

    val hours = numbers.substring(0, 2).toIntOrNull() ?: return null
    val minutes = if (numbers.length >= 4) numbers.substring(2, 4).toIntOrNull() ?: 0 else 0
    return runCatching { ZoneOffset.ofHoursMinutes(sign * hours, sign * minutes) }.getOrNull()
}

internal enum class RelativeDateUnit { TODAY, YESTERDAY, DAYS, WEEKS, MONTHS, YEARS }

internal fun relativeDateBucket(date: LocalDate, today: LocalDate): Pair<RelativeDateUnit, Int> {
    val days = ChronoUnit.DAYS.between(date, today)
    return when {
        days <= 0L -> RelativeDateUnit.TODAY to 0
        days == 1L -> RelativeDateUnit.YESTERDAY to 1
        days < 7L -> RelativeDateUnit.DAYS to days.toInt()
        days < 30L -> RelativeDateUnit.WEEKS to (days / 7L).toInt()
        days < 365L -> RelativeDateUnit.MONTHS to ChronoUnit.MONTHS.between(date, today).toInt().coerceAtLeast(1)
        else -> RelativeDateUnit.YEARS to ChronoUnit.YEARS.between(date, today).toInt().coerceAtLeast(1)
    }
}
