// Written by Mudlej. License is GPLv3.
package com.gitlab.mudlej.MjPdfReader.suite.core

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class TestDuration(val value: DurationCategory)

enum class DurationCategory {
    SHORT,
    NORMAL,
    LONG,
    VERY_LONG,
}
