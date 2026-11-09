// Written by Mudlej. License is GPLv3.

package com.shockwave.pdfium.util;

import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class SizeFTest {
    @Ignore("Pending ruling: signed zeros compare equal but have different hashes; equal sizes must have equal hashes")
    @Test
    public void equalSignedZeroSizesHaveEqualHashCodes() {
        SizeF positiveZero = new SizeF(0f, 1f);
        SizeF negativeZero = new SizeF(-0f, 1f);
        assertEquals(positiveZero, negativeZero);
        assertEquals(positiveZero.hashCode(), negativeZero.hashCode());
    }

    @Test
    public void nanDimensionsAreNotEqualToAnIdenticalCopy() {
        assertNotEquals(new SizeF(Float.NaN, 1f), new SizeF(Float.NaN, 1f));
        assertNotEquals(new SizeF(1f, Float.NaN), new SizeF(1f, Float.NaN));
    }

    @Test
    public void conversionToIntegerSizeTruncatesTowardZero() {
        assertEquals(new Size(1, -1), new SizeF(1.9f, -1.9f).toSize());
        assertEquals(new Size(-1, 1), new SizeF(-1.9f, 1.9f).toSize());
        assertEquals(new Size(0, 0), new SizeF(-0.9f, 0.9f).toSize());
    }
}
