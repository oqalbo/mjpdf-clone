// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.macrobenchmark

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class PerfTest(val value: String)
