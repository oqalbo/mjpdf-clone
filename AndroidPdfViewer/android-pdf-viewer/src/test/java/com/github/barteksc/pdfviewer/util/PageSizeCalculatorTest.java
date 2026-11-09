// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer.util;

import static org.junit.Assert.assertEquals;

import com.shockwave.pdfium.util.Size;
import com.shockwave.pdfium.util.SizeF;

import org.junit.Test;

public class PageSizeCalculatorTest {

    @Test
    public void fitWidthKeepsFractionalWidthAndFloorsHeight() {
        PageSizeCalculator calculator = new PageSizeCalculator(FitPolicy.WIDTH,
                new Size(400, 200), new Size(200, 600), new Size(301, 500), false);

        assertSize(150.5f, 226f, calculator.calculate(new Size(200, 301)));
    }

    @Test
    public void fitBothUsesTheHeightLimitAndFloorsWidthForTallPages() {
        PageSizeCalculator calculator = new PageSizeCalculator(FitPolicy.BOTH,
                new Size(1000, 1000), new Size(1000, 1000), new Size(300, 500), true);

        assertSize(166f, 500f, calculator.calculate(new Size(200, 601)));
    }

    @Test
    public void fitEachPageUsesTheViewInsteadOfTheLargestPageRatios() {
        for (FitPolicy policy : FitPolicy.values()) {
            PageSizeCalculator calculator = new PageSizeCalculator(policy,
                    new Size(2000, 1000), new Size(1000, 4000), new Size(300, 600), true);

            assertSize(300f, 600f, calculator.calculate(new Size(100, 200)));
            assertSize(300f, 600f, calculator.calculate(new Size(400, 800)));
        }
    }

    @Test
    public void nonPositivePageDimensionsReturnZeroSizeForEveryPolicy() {
        Size[] invalid = {new Size(0, 10), new Size(10, 0), new Size(-1, 10),
                new Size(10, -1), new Size(0, 0)};
        for (FitPolicy policy : FitPolicy.values()) {
            PageSizeCalculator calculator = calculator(policy);
            for (Size size : invalid) {
                assertSize(0f, 0f, calculator.calculate(size));
            }
        }
    }

    @Test
    public void mixedPageWidthsKeepTheirRatioWhenFittingTogether() {
        PageSizeCalculator calculator = calculator(FitPolicy.WIDTH);

        assertSize(300f, 150f, calculator.calculate(new Size(400, 200)));
        assertSize(150f, 75f, calculator.calculate(new Size(200, 100)));
    }

    @Test
    public void fitHeightOnLandscapePagesFloorsWidthAndKeepsViewHeight() {
        PageSizeCalculator calculator = new PageSizeCalculator(FitPolicy.HEIGHT,
                new Size(400, 200), new Size(200, 800), new Size(300, 501), true);

        assertSize(1000f, 501f, calculator.calculate(new Size(601, 301)));
    }

    @Test
    public void fitWidthScalesBothMaximumPagesByTheWidestPage() {
        PageSizeCalculator calculator = calculator(FitPolicy.WIDTH);

        assertSize(300f, 150f, calculator.getOptimalMaxWidthPageSize());
        assertSize(150f, 600f, calculator.getOptimalMaxHeightPageSize());
    }

    @Test
    public void fitHeightScalesBothMaximumPagesByTheTallestPage() {
        PageSizeCalculator calculator = calculator(FitPolicy.HEIGHT);

        assertSize(250f, 125f, calculator.getOptimalMaxWidthPageSize());
        assertSize(125f, 500f, calculator.getOptimalMaxHeightPageSize());
    }

    @Test
    public void fitBothRestrictsBothMaximumPagesToTheSharedHeightRatio() {
        PageSizeCalculator calculator = calculator(FitPolicy.BOTH);

        assertSize(250f, 125f, calculator.getOptimalMaxWidthPageSize());
        assertSize(125f, 500f, calculator.getOptimalMaxHeightPageSize());
    }

    private static PageSizeCalculator calculator(FitPolicy policy) {
        return new PageSizeCalculator(policy, new Size(400, 200), new Size(200, 800),
                new Size(300, 500), false);
    }

    private static void assertSize(float width, float height, SizeF actual) {
        assertEquals(width, actual.getWidth(), 0f);
        assertEquals(height, actual.getHeight(), 0f);
    }
}
