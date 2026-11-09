// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CropBoundsTest {
    @Test
    public void factoryClampsFiniteCoordinatesBeforeCheckingSize() {
        CropBounds bounds = CropBounds.of(-0.2f, 0.2f, 1.2f, 0.8f);
        assertEquals(0f, bounds.getLeft(), 0f);
        assertEquals(0.2f, bounds.getTop(), 0f);
        assertEquals(1f, bounds.getRight(), 0f);
        assertEquals(0.8f, bounds.getBottom(), 0f);
    }

    @Test
    public void reversedOrTooNarrowClampedBoundsFallBackToFullPage() {
        assertTrue(CropBounds.of(0.7f, 0f, 0.3f, 1f).isFullPage());
        assertTrue(CropBounds.of(-0.5f, 0f, 0.005f, 1f).isFullPage());
        assertTrue(CropBounds.of(0f, 0.995f, 1f, 1.5f).isFullPage());
    }

    @Test
    public void nonFiniteCoordinatesFallBackToFullPage() {
        for (float value : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            assertTrue(CropBounds.of(value, 0.2f, 0.8f, 0.8f).isFullPage());
            assertTrue(CropBounds.of(0.2f, value, 0.8f, 0.8f).isFullPage());
            assertTrue(CropBounds.of(0.2f, 0.2f, value, 0.8f).isFullPage());
            assertTrue(CropBounds.of(0.2f, 0.2f, 0.8f, value).isFullPage());
        }
    }

    @Test
    public void storedCoordinatesRejectWhitespaceNonFiniteAndOutOfRangeValues() {
        for (String value : new String[]{null, "", "0,0,1", "0,0,1,1,1", " 0.1,0,1,1",
                "0,0,1,1 ", "NaN,0,1,1", "0,0,Infinity,1", "-0.1,0,1,1",
                "0,0,1.1,1", "0.7,0,0.3,1", "0,0,0.005,1"}) {
            assertNull(value, CropBounds.fromStorageString(value));
        }
    }

    @Test
    public void acceptedNarrowBoundsAreStoredWithSixDecimalPlaces() {
        CropBounds bounds = CropBounds.of(0.1f, 0f, 0.1100001f, 1f);
        assertFalse(bounds.isFullPage());
        assertTrue(bounds.getWidth() > 0.01f);
        assertEquals("0.100000,0.000000,0.110000,1.000000", bounds.toStorageString());
    }
}
