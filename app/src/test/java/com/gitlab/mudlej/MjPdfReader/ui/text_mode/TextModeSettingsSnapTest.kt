// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.text_mode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.abs
import kotlin.math.roundToLong

class TextModeSettingsSnapTest {

    private val fontSize = Domain(
        TextModeSettings.FONT_SIZE_MIN,
        TextModeSettings.FONT_SIZE_MAX,
        TextModeSettings.FONT_SIZE_STEP,
        TextModeSettings.DEFAULT_FONT_SIZE,
    )

    private val lineSpacing = Domain(
        TextModeSettings.LINE_SPACING_MIN,
        TextModeSettings.LINE_SPACING_MAX,
        TextModeSettings.LINE_SPACING_STEP,
        TextModeSettings.DEFAULT_LINE_SPACING,
    )

    private val horizontalMargin = Domain(
        TextModeSettings.HORIZONTAL_MARGIN_MIN,
        TextModeSettings.HORIZONTAL_MARGIN_MAX,
        TextModeSettings.HORIZONTAL_MARGIN_STEP,
        TextModeSettings.DEFAULT_HORIZONTAL_MARGIN.toFloat(),
    )

    private val domains = listOf(fontSize, lineSpacing, horizontalMargin)

    @Test
    fun shippedDefaultsAreBitIdenticalFixedPoints() {
        for (domain in domains) {
            assertEquals(domain.default.toRawBits(), domain.snap(domain.default).toRawBits())
        }
    }

    @Test
    fun boundsAreFixedPoints() {
        for (domain in domains) {
            assertEquals(domain.min, domain.snap(domain.min), 0f)
            assertEquals(domain.max, domain.snap(domain.max), 0f)
        }
    }

    @Test
    fun valuesFarAboveTheRangeClampToTheMaximum() {
        assertEquals(36f, fontSize.snap(4000f), 0f)
        assertEquals(2.2f, lineSpacing.snap(99f), 0f)
        assertEquals(48f, horizontalMargin.snap(100000f), 0f)
    }

    @Test
    fun valuesFarBelowTheRangeClampToTheMinimum() {
        assertEquals(12f, fontSize.snap(-4000f), 0f)
        assertEquals(1f, lineSpacing.snap(0f), 0f)
        assertEquals(8f, horizontalMargin.snap(-1f), 0f)
    }

    @Test
    fun nonFiniteValuesFallBackToTheShippedDefault() {
        for (domain in domains) {
            assertEquals(domain.default, domain.snap(Float.NaN), 0f)
            assertEquals(domain.default, domain.snap(Float.POSITIVE_INFINITY), 0f)
            assertEquals(domain.default, domain.snap(Float.NEGATIVE_INFINITY), 0f)
        }
    }

    @Test
    fun offGridValuesSnapToTheNearestTick() {
        assertEquals(19f, fontSize.snap(18.5f), 0f)
        assertEquals(18f, fontSize.snap(18.4f), 0f)
        assertEquals(1.35f, lineSpacing.snap(1.37f), 0f)
        assertEquals(1.4f, lineSpacing.snap(1.38f), 0f)
        assertEquals(22f, horizontalMargin.snap(21f), 0f)
        assertEquals(20f, horizontalMargin.snap(20.9f), 0f)
    }

    @Test
    fun everySnappedValueIsAcceptedByTheSliderTickCheck() {
        for (domain in domains) {
            var raw = domain.min - 2f
            while (raw <= domain.max + 2f) {
                val snapped = domain.snap(raw)
                assertTrue(
                    "$raw snapped to $snapped which is outside the slider range",
                    snapped >= domain.min && snapped <= domain.max,
                )
                assertTrue(
                    "$raw snapped to $snapped which is not on the slider tick grid",
                    landsOnTick(snapped, domain.min, domain.step),
                )
                raw += 0.01f
            }
        }
    }

    private fun landsOnTick(value: Float, valueFrom: Float, stepSize: Float): Boolean {
        val offset = BigDecimal(value.toString())
            .subtract(BigDecimal(valueFrom.toString()), MathContext.DECIMAL64)
            .toDouble()
        val ticks = BigDecimal(offset.toString())
            .divide(BigDecimal(stepSize.toString()), MathContext.DECIMAL64)
            .toDouble()
        return abs(ticks.roundToLong() - ticks) < 0.0001
    }

    private class Domain(
        val min: Float,
        val max: Float,
        val step: Float,
        val default: Float,
    ) {
        fun snap(value: Float): Float = TextModeSettings.snap(value, min, max, step, default)
    }
}
