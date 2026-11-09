// Written by Mudlej. License is GPLv3.
package com.gitlab.mudlej.MjPdfReader.suite.core

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class RequiresFixture(vararg val value: FixtureResource)

enum class FixtureResource {
    HUGE_PDF,
    LARGE_PDF,
    OVERSIZED_BACKUP,
}
