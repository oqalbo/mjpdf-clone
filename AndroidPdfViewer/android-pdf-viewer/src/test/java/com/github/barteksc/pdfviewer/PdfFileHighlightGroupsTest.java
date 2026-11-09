// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer;

import com.github.barteksc.pdfviewer.support.PdfiumTestSupport;
import com.shockwave.pdfium.PdfDocument.HighlightAnnotation;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import static com.github.barteksc.pdfviewer.support.Rects.rectF;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PdfFileHighlightGroupsTest extends PdfiumTestSupport.InitializedTest {
    private PdfFileTestSupport fixture;

    @Before
    public void setUp() {
        fixture = PdfFileTestSupport.uniform(2, 100, 200).spacing(0).textPages();
        fixture.build();
    }

    @Test
    public void cachedGroupsSplitColorAndOwnershipWhileHitTestingMergesTheSameKey() {
        HighlightAnnotation first = new HighlightAnnotation(0, "shared", rectF(0, 10, 10, 0), "", 1, true);
        HighlightAnnotation second = new HighlightAnnotation(1, "shared", rectF(20, 30, 30, 20), "", 2, true);
        HighlightAnnotation third = new HighlightAnnotation(2, "shared", rectF(40, 50, 50, 40), "", 2, false);
        HighlightAnnotation fourth = new HighlightAnnotation(3, "shared", rectF(60, 70, 70, 60), "", 1, true);
        List<HighlightAnnotation> annotations = Arrays.asList(first, second, third, fourth);
        Map<String, List<HighlightAnnotation>> groups = fixture.file.peekHighlightAnnotationGroups(0, annotations);
        assertEquals(3, groups.size());
        assertEquals(Arrays.asList(first, fourth), groups.get("gshared#1#true"));
        assertEquals(Collections.singletonList(second), groups.get("gshared#2#true"));
        assertEquals(Collections.singletonList(third), groups.get("gshared#2#false"));
        assertArrayEquals(new float[]{0, 70, 70, 0},
                HighlightHitTesting.unionGroupBounds(annotations, "shared", null), 0);
        assertSame(first, HighlightHitTesting.findHighlightAnnotationMatching(annotations,
                Arrays.asList(first.getBounds(), second.getBounds(), third.getBounds(), fourth.getBounds())));
    }

    @Test
    public void groupsSkipSearchResultsAndMissingBoundsAndKeepUngroupedAnnotationsSeparate() {
        HighlightAnnotation search = new HighlightAnnotation(0, "shared", rectF(0, 10, 10, 0), "",
                1, true, true, "", "");
        HighlightAnnotation missing = new HighlightAnnotation(1, "shared", null, "");
        HighlightAnnotation empty = new HighlightAnnotation(2, "", rectF(0, 10, 10, 0), "");
        HighlightAnnotation absent = new HighlightAnnotation(3, null, rectF(0, 10, 10, 0), "");
        Map<String, List<HighlightAnnotation>> groups = fixture.file.peekHighlightAnnotationGroups(0,
                Arrays.asList(search, missing, empty, absent));
        assertEquals(2, groups.size());
        assertEquals(Collections.singletonList(empty), groups.get("i2"));
        assertEquals(Collections.singletonList(absent), groups.get("i3"));
    }

    @Test
    public void pageInvalidationRefreshesItsGroupsAndNativeSnapshotOnly() {
        HighlightAnnotation old = new HighlightAnnotation(0, "old", rectF(0, 10, 10, 0), "");
        HighlightAnnotation fresh = new HighlightAnnotation(1, "fresh", rectF(20, 30, 30, 20), "");
        when(fixture.core.getHighlightAnnotations(fixture.document, 0))
                .thenReturn(Collections.singletonList(old), Collections.singletonList(fresh));
        List<HighlightAnnotation> snapshot = fixture.file.getHighlightAnnotations(0);
        Map<String, List<HighlightAnnotation>> cached = fixture.file.peekHighlightAnnotationGroups(0, snapshot);
        Map<String, List<HighlightAnnotation>> otherPage = fixture.file.peekHighlightAnnotationGroups(1, snapshot);
        assertSame(snapshot, fixture.file.getHighlightAnnotations(0));
        assertSame(cached, fixture.file.peekHighlightAnnotationGroups(0, Collections.singletonList(fresh)));
        fixture.file.invalidateHighlightAnnotationCache(0);
        assertNull(fixture.file.peekHighlightAnnotations(0));
        List<HighlightAnnotation> refreshed = fixture.file.getHighlightAnnotations(0);
        assertEquals(Collections.singletonList(fresh), refreshed);
        Map<String, List<HighlightAnnotation>> rebuilt = fixture.file.peekHighlightAnnotationGroups(0, refreshed);
        assertEquals(Collections.singletonList(fresh), rebuilt.get("gfresh#-256#false"));
        assertEquals(1, rebuilt.size());
        assertNotSame(cached, rebuilt);
        assertSame(otherPage, fixture.file.peekHighlightAnnotationGroups(1, refreshed));
        verify(fixture.core, times(2)).getHighlightAnnotations(fixture.document, 0);
    }

    @Test
    public void globalInvalidationDropsGroupsForEveryPage() {
        HighlightAnnotation annotation = new HighlightAnnotation(0, "group", rectF(0, 10, 10, 0), "");
        List<HighlightAnnotation> annotations = Collections.singletonList(annotation);
        fixture.file.peekHighlightAnnotationGroups(0, annotations);
        fixture.file.peekHighlightAnnotationGroups(1, annotations);
        fixture.file.invalidateHighlightAnnotationCache();
        assertEquals(Collections.emptyMap(), fixture.file.peekHighlightAnnotationGroups(0, Collections.emptyList()));
        assertEquals(Collections.emptyMap(), fixture.file.peekHighlightAnnotationGroups(1, Collections.emptyList()));
    }
}
