// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.rules

@java.lang.annotation.Inherited
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class RequiresSoftwareRenderer

@java.lang.annotation.Inherited
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class RequiresDeviceProfile(val value: String)

@java.lang.annotation.Inherited
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class SoftwareRendererPerformance
