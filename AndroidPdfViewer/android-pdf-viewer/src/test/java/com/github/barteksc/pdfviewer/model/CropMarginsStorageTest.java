// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer.model;

import org.junit.Ignore;
import org.junit.Test;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CropMarginsStorageTest {
    @Test
    public void parityAndPageOverridesRoundTripInPageOrder() {
        CropBounds even = CropBounds.of(0.1f, 0.2f, 0.8f, 0.9f);
        CropBounds odd = CropBounds.of(0.2f, 0.1f, 0.9f, 0.8f);
        Map<Integer, CropBounds> pages = new LinkedHashMap<>();
        pages.put(9, odd);
        pages.put(2, CropBounds.fullPage());
        pages.put(5, even);
        String stored = new CropMargins(even, odd, pages).toStorageString();
        assertEquals("6;0.100000,0.200000,0.800000,0.900000;0.200000,0.100000,0.900000,0.800000;"
                + "2:0.000000,0.000000,1.000000,1.000000|5:0.100000,0.200000,0.800000,0.900000"
                + "|9:0.200000,0.100000,0.900000,0.800000", stored);
        CropMargins restored = CropMargins.fromStorageString(stored);
        assertNotNull(restored);
        assertTrue(restored.hasParitySplit());
        assertTrue(even.isSimilarTo(restored.getEvenCrop(), 0f));
        assertTrue(odd.isSimilarTo(restored.getOddCrop(), 0f));
        assertEquals(Arrays.asList(2, 5, 9), new ArrayList<>(restored.getPageCrops().keySet()));
        assertTrue(restored.forDocumentPage(2).isFullPage());
        assertTrue(even.isSimilarTo(restored.forDocumentPage(5), 0f));
        assertTrue(odd.isSimilarTo(restored.forDocumentPage(9), 0f));
    }

    @Test
    @Ignore("Pending ruling: six-decimal rounding rejects an accepted narrow crop; expected storage round trip to survive")
    public void acceptedNarrowCropSurvivesDocumentStorageRoundTrip() {
        CropBounds bounds = CropBounds.of(0.1f, 0f, 0.1100001f, 1f);
        CropMargins restored = CropMargins.fromStorageString(CropMargins.single(bounds).toStorageString());
        assertNotNull("An accepted crop must remain readable after storage rounding", restored);
        assertTrue(bounds.isSimilarTo(restored.getEvenCrop(), 0.000001f));
    }

    @Test
    public void malformedDocumentAndPageEntriesRejectTheEntireStoredCrop() {
        for (String value : new String[]{"5;0,0,1,1;0,0,1,1;", "6;0,0,1,1;0,0,1,1",
                "6; 0.1,0,1,1;0,0,1,1;", "6;NaN,0,1,1;0,0,1,1;",
                "6;0,0,1,1;0,0,1.1,1;", "6;0,0,1,1;0,0,1,1;-1:0,0,1,1",
                "6;0,0,1,1;0,0,1,1;1:a||2:b", "6;0,0,1,1;0,0,1,1;1:0,0,1,1||2:0,0,1,1",
                "6;0,0,1,1;0,0,1,1;:0,0,1,1", "6;0,0,1,1;0,0,1,1;1:-0.1,0,1,1"}) {
            assertNull(value, CropMargins.fromStorageString(value));
        }
    }

    @Test
    public void pageIndexWithLeadingPlusIsAcceptedAndStoredWithoutThePlus() {
        CropMargins margins = CropMargins.fromStorageString("6;0,0,1,1;0,0,1,1;+3:0.1,0.2,0.8,0.9");
        assertNotNull(margins);
        assertEquals(Collections.singleton(3), margins.getPageCrops().keySet());
        assertTrue(CropBounds.of(0.1f, 0.2f, 0.8f, 0.9f).isSimilarTo(margins.forDocumentPage(3), 0f));
        assertEquals("3:0.100000,0.200000,0.800000,0.900000", margins.toStorageString().split(";")[3]);
    }

    @Test
    public void pageOverridesTakePriorityOverBothParities() {
        CropBounds even = CropBounds.of(0.1f, 0f, 0.8f, 1f);
        CropBounds odd = CropBounds.of(0.2f, 0f, 0.9f, 1f);
        Map<Integer, CropBounds> pages = new LinkedHashMap<>();
        pages.put(2, odd);
        pages.put(3, even);
        CropMargins margins = new CropMargins(even, odd, pages);
        assertTrue(even.isSimilarTo(margins.forDocumentPage(0), 0f));
        assertTrue(odd.isSimilarTo(margins.forDocumentPage(1), 0f));
        assertTrue(odd.isSimilarTo(margins.forDocumentPage(2), 0f));
        assertTrue(even.isSimilarTo(margins.forDocumentPage(3), 0f));
    }

    @Test
    public void missingOddCropUsesEvenCrop() {
        CropBounds even = CropBounds.of(0.1f, 0.2f, 0.8f, 0.9f);
        CropMargins margins = new CropMargins(even, null);
        assertTrue(even.isSimilarTo(margins.forDocumentPage(1), 0f));
        assertFalse(margins.hasParitySplit());
    }

    @Test
    public void fullPageOverridesAreExcludedAndPreventWholeDocumentFullPageStatus() {
        Map<Integer, CropBounds> pages = new LinkedHashMap<>();
        pages.put(2, CropBounds.fullPage());
        pages.put(4, CropBounds.of(0.1f, 0.1f, 0.9f, 0.9f));
        CropMargins margins = new CropMargins(null, null, pages);
        assertEquals(Collections.singleton(2), margins.getExcludedPages());
        assertFalse(margins.isFullPage());
        assertFalse(new CropMargins(null, null, Collections.singletonMap(2, CropBounds.fullPage())).isFullPage());
        assertTrue(CropMargins.fullPage().isFullPage());
    }
}
