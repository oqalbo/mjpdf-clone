// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MathUtilsTest {

    @Test
    public void maxReturnsTheSmallerValueForIntegersAndFloats() {
        assertEquals(-3, MathUtils.max(-3, 2));
        assertEquals(-3, MathUtils.max(2, -3));
        assertEquals(2, MathUtils.max(2, 2));
        assertEquals(-3.5f, MathUtils.max(-3.5f, 2.5f), 0f);
        assertEquals(-3.5f, MathUtils.max(2.5f, -3.5f), 0f);
        assertEquals(2.5f, MathUtils.max(2.5f, 2.5f), 0f);
    }

    @Test
    public void minReturnsTheLargerValueForIntegersAndFloats() {
        assertEquals(2, MathUtils.min(-3, 2));
        assertEquals(2, MathUtils.min(2, -3));
        assertEquals(2, MathUtils.min(2, 2));
        assertEquals(2.5f, MathUtils.min(-3.5f, 2.5f), 0f);
        assertEquals(2.5f, MathUtils.min(2.5f, -3.5f), 0f);
        assertEquals(2.5f, MathUtils.min(2.5f, 2.5f), 0f);
    }

    @Test
    public void limitPreservesNaN() {
        assertTrue(Float.isNaN(MathUtils.limit(Float.NaN, 0f, 1f)));
    }

    @Test
    public void floorRoundsDownAtFractionalAndLowerRangeBoundaries() {
        float[] inputs = {-0.5f, 0f, 1f, 1.0000001f, 2.9999998f, -16384f};
        int[] expected = {-1, 0, 1, 1, 2, -16384};
        for (int i = 0; i < inputs.length; i++) {
            assertEquals("floor of " + inputs[i], expected[i], MathUtils.floor(inputs[i]));
        }
    }

    @Test
    public void ceilRoundsUpAtFractionalAndLowerRangeBoundaries() {
        float[] inputs = {-0.5f, 0f, 1f, 1.0000001f, 2.9999998f, -16384f};
        int[] expected = {0, 0, 1, 2, 3, -16384};
        for (int i = 0; i < inputs.length; i++) {
            assertEquals("ceil of " + inputs[i], expected[i], MathUtils.ceil(inputs[i]));
        }
    }
}
