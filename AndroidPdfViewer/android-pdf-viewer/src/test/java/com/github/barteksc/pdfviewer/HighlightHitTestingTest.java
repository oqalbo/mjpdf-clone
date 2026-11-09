// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer;

import android.graphics.RectF;
import com.shockwave.pdfium.PdfDocument.HighlightAnnotation;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Ignore;
import org.junit.Test;
import static com.github.barteksc.pdfviewer.support.Rects.rectF;
import static org.junit.Assert.*;

public class HighlightHitTestingTest {
    @Test
    public void formFieldToleranceIncludesAllFourEdgesWithInvertedCorners() {
        float[] rects = {20, 40, 10, 30, 100, 100, 200};
        float[][] edges = {{8, 35}, {22, 35}, {15, 28}, {15, 42}};
        float[][] outside = {{7.99f, 35}, {22.01f, 35}, {15, 27.99f}, {15, 42.01f}};
        for (int side = 0; side < edges.length; side++) {
            assertTrue("edge " + side, HighlightHitTesting.pointNearFormFieldRect(rects, edges[side][0], edges[side][1], 2));
            assertFalse("outside " + side, HighlightHitTesting.pointNearFormFieldRect(rects, outside[side][0], outside[side][1], 2));
        }
        assertFalse(HighlightHitTesting.pointNearFormFieldRect(rects, 150, 150, 2));
        assertFalse(HighlightHitTesting.pointNearFormFieldRect(new float[]{100, 100, 200}, 150, 150, 2));
    }

    @Test
    public void rectangleToleranceNormalizesCornersAndChecksEveryCoordinate() {
        RectF reference = rectF(10, 40, 20, 30);
        assertTrue(HighlightHitTesting.pdfRectsAlmostEqual(reference, rectF(21.5f, 28.5f, 8.5f, 41.5f), 1.5f));
        float[][] outside = {{8.49f, 40, 20, 30}, {10, 41.51f, 20, 30},
                {10, 40, 21.51f, 30}, {10, 40, 20, 28.49f}};
        for (float[] bounds : outside) {
            assertFalse(HighlightHitTesting.pdfRectsAlmostEqual(reference,
                    rectF(bounds[0], bounds[1], bounds[2], bounds[3]), 1.5f));
        }
    }

    @Test
    public void pointToleranceIncludesBoundaryAndRejectsPointsBeyondEachSide() {
        RectF bounds = rectF(20, 30, 10, 40);
        float[][] edges = {{8, 35}, {22, 35}, {15, 28}, {15, 42}};
        float[][] outside = {{7.99f, 35}, {22.01f, 35}, {15, 27.99f}, {15, 42.01f}};
        for (int side = 0; side < edges.length; side++) {
            assertTrue(HighlightHitTesting.pdfRectContainsPoint(bounds, edges[side][0], edges[side][1], 2));
            assertFalse(HighlightHitTesting.pdfRectContainsPoint(bounds, outside[side][0], outside[side][1], 2));
        }
    }

    @Test
    public void groupMatchingConsumesEachMemberOnceAndIgnoresRectOrder() {
        HighlightAnnotation first = annotation(0, "group", 0);
        HighlightAnnotation second = annotation(1, "group", 20);
        List<HighlightAnnotation> members = Arrays.asList(first, second);
        assertTrue(HighlightHitTesting.groupMatchesRects(members, Arrays.asList(second.getBounds(), first.getBounds())));
        assertFalse(HighlightHitTesting.groupMatchesRects(members, Collections.singletonList(first.getBounds())));
        assertFalse(HighlightHitTesting.groupMatchesRects(members, Arrays.asList(first.getBounds(), first.getBounds())));
        assertFalse(HighlightHitTesting.groupMatchesRects(
                Collections.singletonList(new HighlightAnnotation(2, "group", null, "")),
                Collections.singletonList(first.getBounds())));
    }

    @Ignore("Pending ruling: greedy matching rejects overlapping tolerances despite a valid one-to-one assignment")
    @Test
    public void overlappingTolerancesFindAValidAssignmentInsteadOfTakingTheFirstFit() {
        List<HighlightAnnotation> members = Arrays.asList(annotation(0, "group", 0), annotation(1, "group", 2));
        assertTrue(HighlightHitTesting.groupMatchesRects(members,
                Arrays.asList(rectF(1, 10, 11, 0), rectF(0, 10, 10, 0))));
    }

    @Ignore("Pending ruling: HashMap iteration chooses a later matching group instead of the first inserted group")
    @Test
    public void firstInsertedGroupWinsWhenTwoGroupsMatch() {
        HighlightAnnotation first = annotation(0, "z", 0);
        HighlightAnnotation second = annotation(1, "a", 0);
        assertSame(first, HighlightHitTesting.findHighlightAnnotationMatching(Arrays.asList(first, second),
                Collections.singletonList(first.getBounds())));
    }

    @Test
    public void matchingSkipsUngroupedAnnotationsAndRejectsUnmatchedGroups() {
        HighlightAnnotation ungrouped = annotation(0, "", 0);
        HighlightAnnotation grouped = annotation(1, "group", 20);
        List<HighlightAnnotation> annotations = Arrays.asList(ungrouped, grouped);
        assertNull(HighlightHitTesting.findHighlightAnnotationMatching(annotations,
                Collections.singletonList(ungrouped.getBounds())));
        assertSame(grouped, HighlightHitTesting.findHighlightAnnotationMatching(annotations,
                Collections.singletonList(grouped.getBounds())));
    }

    @Test
    public void boundsLookupPrefersTheGroupAndFallsBackToTheAnnotationIndex() {
        HighlightAnnotation first = annotation(0, "group", 0);
        HighlightAnnotation second = annotation(1, "group", 20);
        List<HighlightAnnotation> annotations = Arrays.asList(first, second);
        assertSame(first.getBounds(), HighlightHitTesting.findHighlightPdfBounds(annotations, "group", 1));
        assertSame(second.getBounds(), HighlightHitTesting.findHighlightPdfBounds(annotations, "missing", 1));
        assertSame(second.getBounds(), HighlightHitTesting.findHighlightPdfBounds(annotations, "", 1));
        assertNull(HighlightHitTesting.findHighlightPdfBounds(annotations, null, 9));
    }

    @Test
    public void unionNormalizesMatchingBoundsAndIgnoresOtherGroupsAndMissingBounds() {
        List<HighlightAnnotation> annotations = Arrays.asList(annotation(0, "group", 0),
                new HighlightAnnotation(1, "group", rectF(30, 5, 20, 15), ""),
                annotation(2, "other", 100), new HighlightAnnotation(3, "group", null, ""));
        assertArrayEquals(new float[]{0, 15, 30, 0}, HighlightHitTesting.unionGroupBounds(annotations, "group", null), 0);
        RectF fallback = rectF(5, 10, 15, 0);
        assertArrayEquals(new float[]{5, 10, 15, 0}, HighlightHitTesting.unionGroupBounds(annotations, "missing", fallback), 0);
        assertArrayEquals(new float[]{5, 10, 15, 0}, HighlightHitTesting.unionGroupBounds(annotations, "", fallback), 0);
        assertNull(HighlightHitTesting.unionGroupBounds(annotations, null, null));
    }

    private static HighlightAnnotation annotation(int index, String group, float left) {
        return new HighlightAnnotation(index, group, rectF(left, 10, left + 10, 0), "");
    }
}
