// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer.preview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PreviewKeyTest {

    @Test
    public void equalsAndHashCode_matchOnAllThreeFields() {
        PreviewKey a = new PreviewKey(3, 512, 7);
        PreviewKey b = new PreviewKey(3, 512, 7);
        assertTrue(a.equals(b));
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void tagChange_producesDistinctKey() {
        PreviewKey base = new PreviewKey(3, 512, 0);
        assertNotEquals(base, new PreviewKey(3, 512, 1));
    }

    @Test
    public void bucketChange_producesDistinctKey() {
        PreviewKey base = new PreviewKey(3, 512, 0);
        assertNotEquals(base, new PreviewKey(3, 256, 0));
    }

    @Test
    public void pageChange_producesDistinctKey() {
        PreviewKey base = new PreviewKey(3, 512, 0);
        assertNotEquals(base, new PreviewKey(4, 512, 0));
    }

    @Test
    public void notEqualToOtherTypes() {
        assertFalse(new PreviewKey(1, 1, 1).equals("p1-w1-t1"));
    }
}
