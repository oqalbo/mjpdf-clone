// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TextDirectionUtilTest {

    @Test
    public void rightToLeftRangesIncludeBothEndpoints() {
        int[] endpoints = {0x0590, 0x08FF, 0xFB1D, 0xFDFF, 0xFE70, 0xFEFF};
        for (int codePoint : endpoints) {
            assertTrue(Integer.toHexString(codePoint), TextDirectionUtil.isRtl(codePoint));
        }
    }

    @Test
    public void adjacentCodePointsOutsideEachRangeAreNotRightToLeft() {
        int[] outside = {0x058F, 0x0900, 0xFB1C, 0xFE00, 0xFE6F, 0xFF00};
        for (int codePoint : outside) {
            assertFalse(Integer.toHexString(codePoint), TextDirectionUtil.isRtl(codePoint));
        }
    }
}
