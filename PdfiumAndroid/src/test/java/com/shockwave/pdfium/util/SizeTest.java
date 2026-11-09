// Written by Mudlej. License is GPLv3.

package com.shockwave.pdfium.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class SizeTest {
    @Test
    public void swappingUnequalDimensionsChangesTheSize() {
        Size portrait = new Size(3, 7);
        Size landscape = new Size(7, 3);
        assertNotEquals(portrait, landscape);
        assertNotEquals(landscape, portrait);
        assertEquals(portrait, new Size(3, 7));
        assertEquals(portrait.hashCode(), new Size(3, 7).hashCode());
        assertEquals(landscape, new Size(7, 3));
        assertEquals(landscape.hashCode(), new Size(7, 3).hashCode());
    }

    @Test
    public void squareDimensionsRemainEqualWhenSwapped() {
        Size square = new Size(7, 7);
        Size swapped = new Size(square.getHeight(), square.getWidth());
        assertEquals(square, swapped);
        assertEquals(square.hashCode(), swapped.hashCode());
    }
}
