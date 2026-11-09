// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer;

import com.github.barteksc.pdfviewer.model.CropBounds;
import com.github.barteksc.pdfviewer.model.CropMargins;

import org.junit.Ignore;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.github.barteksc.pdfviewer.CropTestSupport.assertBounds;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

public class CropMarginDetectorAggregateTest {
    @Test
    public void fourSamplesOfEachParityKeepDistinctGutters() {
        List<CropMarginDetector.Sample> samples = alternating(4, 4, 0.2f, 0.3f);
        CropMargins margins = CropMarginDetector.aggregate(samples, samples.size());
        assertTrue(margins.hasParitySplit());
        assertBounds(margins.getEvenCrop(), 0.185f, 0.185f, 0.815f, 0.815f);
        assertBounds(margins.getOddCrop(), 0.285f, 0.185f, 0.815f, 0.815f);
    }

    @Test
    public void guttersWithinParityToleranceCollapseToOneCrop() {
        List<CropMarginDetector.Sample> samples = alternating(4, 4, 0.2f, 0.214f);
        CropMargins margins = CropMarginDetector.aggregate(samples, samples.size());
        assertFalse(margins.hasParitySplit());
        assertBounds(margins.getEvenCrop(), 0.185f, 0.185f, 0.815f, 0.815f);
        assertBounds(margins.getOddCrop(), 0.185f, 0.185f, 0.815f, 0.815f);
    }

    @Test
    public void eitherParityWithFewerThanFourSamplesPreventsASplit() {
        for (int[] counts : new int[][]{{3, 3}, {3, 4}, {4, 3}}) {
            List<CropMarginDetector.Sample> samples = alternating(counts[0], counts[1], 0.2f, 0.3f);
            CropMargins margins = CropMarginDetector.aggregate(samples, samples.size());
            assertFalse(margins.hasParitySplit());
            assertBounds(margins.getEvenCrop(), 0.185f, 0.185f, 0.815f, 0.815f);
        }
    }

    @Test
    public void savingsBelowTwoPercentOnBothAxesFallBackToFullPage() {
        List<CropMarginDetector.Sample> samples = Collections.singletonList(
                new CropMarginDetector.Sample(0, CropBounds.of(0.02f, 0.02f, 0.98f, 0.98f)));
        assertTrue(CropMarginDetector.aggregate(samples, 1).isFullPage());
    }

    @Test
    public void savingsOnOneAxisAreEnoughToKeepACrop() {
        List<CropMarginDetector.Sample> samples = Collections.singletonList(
                new CropMarginDetector.Sample(0, CropBounds.of(0.04f, 0.02f, 0.96f, 0.98f)));
        CropMargins margins = CropMarginDetector.aggregate(samples, 1);
        assertFalse(margins.isFullPage());
        assertBounds(margins.getEvenCrop(), 0.025f, 0.005f, 0.975f, 0.995f);
    }

    @Test
    public void outlyingPageGetsItsOwnPaddedCrop() {
        List<CropMarginDetector.Sample> samples = alternating(4, 4, 0.2f, 0.2f);
        samples.add(new CropMarginDetector.Sample(8, CropBounds.of(0.05f, 0.1f, 0.95f, 0.9f)));
        CropMargins margins = CropMarginDetector.aggregateWithExclusions(samples);
        assertBounds(margins.getEvenCrop(), 0.185f, 0.185f, 0.815f, 0.815f);
        assertEquals(Collections.singleton(8), margins.getPageCrops().keySet());
        assertBounds(margins.forDocumentPage(8), 0.035f, 0.085f, 0.965f, 0.915f);
    }

    @Test
    public void cascadingExclusionsSettleOnTheThirdAggregation() {
        List<CropMarginDetector.Sample> samples = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            float margin = i < 3 ? 0.1f : i < 5 ? 0.2f : 0.3f;
            samples.add(new CropMarginDetector.Sample(i * 2, CropBounds.of(margin, margin, 1f - margin, 1f - margin)));
        }
        try (MockedStatic<CropMarginDetector> detector = mockStatic(CropMarginDetector.class, CALLS_REAL_METHODS)) {
            CropMargins margins = CropMarginDetector.aggregateWithExclusions(samples);
            assertBounds(margins.getEvenCrop(), 0.285f, 0.285f, 0.715f, 0.715f);
            assertEquals(Arrays.asList(0, 2, 4, 6, 8), new ArrayList<>(margins.getPageCrops().keySet()));
            for (int page : new int[]{0, 2, 4}) {
                assertBounds(margins.forDocumentPage(page), 0.085f, 0.085f, 0.915f, 0.915f);
            }
            for (int page : new int[]{6, 8}) {
                assertBounds(margins.forDocumentPage(page), 0.185f, 0.185f, 0.815f, 0.815f);
            }
            detector.verify(() -> CropMarginDetector.aggregate(anyList(), anyInt()), times(3));
        }
    }

    @Test
    public void tighterPageGetsAnOverrideOnlyWhenItSavesFivePercent() {
        List<CropMarginDetector.Sample> samples = alternating(4, 4, 0.2f, 0.2f);
        samples.add(new CropMarginDetector.Sample(8, CropBounds.of(0.26f, 0.26f, 0.74f, 0.74f)));
        samples.add(new CropMarginDetector.Sample(9, CropBounds.of(0.24f, 0.24f, 0.76f, 0.76f)));
        CropMargins margins = CropMarginDetector.aggregateWithExclusions(samples);
        assertEquals(Collections.singleton(8), margins.getPageCrops().keySet());
        assertBounds(margins.forDocumentPage(8), 0.245f, 0.245f, 0.755f, 0.755f);
        assertBounds(margins.forDocumentPage(9), 0.185f, 0.185f, 0.815f, 0.815f);
    }

    @Test
    public void aggregateRequiresFourUsableSamplesForALargeDocument() {
        List<CropMarginDetector.Sample> samples = Collections.singletonList(
                new CropMarginDetector.Sample(0, CropBounds.of(0.2f, 0.2f, 0.8f, 0.8f)));
        assertTrue(CropMarginDetector.aggregate(samples, 100).isFullPage());
    }

    @Test
    @Ignore("Pending ruling: one usable sample out of 100 crops the document; expected full page for insufficient samples")
    public void oneUsableSampleOutOfOneHundredCannotCropTheWholeDocument() {
        List<CropMarginDetector.Sample> samples = new ArrayList<>();
        samples.add(new CropMarginDetector.Sample(0, CropBounds.of(0.2f, 0.2f, 0.8f, 0.8f)));
        for (int page = 1; page < 100; page++) {
            samples.add(new CropMarginDetector.Sample(page, null, null));
        }
        assertTrue(CropMarginDetector.aggregateWithExclusions(samples).isFullPage());
    }

    private static List<CropMarginDetector.Sample> alternating(int evenCount, int oddCount,
                                                               float evenLeft, float oddLeft) {
        List<CropMarginDetector.Sample> samples = new ArrayList<>();
        for (int i = 0; i < evenCount; i++) {
            samples.add(new CropMarginDetector.Sample(i * 2, CropBounds.of(evenLeft, 0.2f, 0.8f, 0.8f)));
        }
        for (int i = 0; i < oddCount; i++) {
            samples.add(new CropMarginDetector.Sample(i * 2 + 1, CropBounds.of(oddLeft, 0.2f, 0.8f, 0.8f)));
        }
        return samples;
    }
}
