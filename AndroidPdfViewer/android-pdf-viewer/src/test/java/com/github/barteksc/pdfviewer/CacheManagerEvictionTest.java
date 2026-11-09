// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.graphics.Bitmap;
import android.graphics.RectF;

import com.github.barteksc.pdfviewer.model.PagePart;
import com.github.barteksc.pdfviewer.util.Constants;

import org.junit.Ignore;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class CacheManagerEvictionTest {

    @Test
    public void equalReplacementRecyclesThePreviousActiveOrPassiveBitmapOnce() {
        for (boolean passive : new boolean[]{false, true}) {
            CacheManager manager = new CacheManager();
            PagePart previous = part(2, 1);
            PagePart replacement = part(2, 9);
            manager.cachePart(previous);
            if (passive) {
                manager.makeANewSet();
            }

            manager.cachePart(replacement);

            assertEquals(1, manager.getPageParts().size());
            assertSame(replacement, manager.getPageParts().get(0));
            verify(previous.getRenderedBitmap(), times(1)).recycle();
            verify(replacement.getRenderedBitmap(), never()).recycle();
            manager.recycle();
            verify(previous.getRenderedBitmap(), times(1)).recycle();
            verify(replacement.getRenderedBitmap(), times(1)).recycle();
        }
    }

    @Test
    public void stalePassiveHitStaysPassiveAndKeepsItsOrder() {
        CacheManager manager = new CacheManager();
        PagePart stale = part(2, 3);
        manager.cachePart(stale);
        manager.makeANewSet();
        stale.markStale();

        assertFalse(manager.upPartIfContained(2, stale.getPageRelativeBounds(), 99));

        assertEquals(Collections.singletonList(stale), manager.getPageParts());
        assertNull(manager.debugActivePart(2, stale.getPageRelativeBounds()));
        assertEquals(3, stale.getCacheOrder());
        verify(stale.getRenderedBitmap(), never()).recycle();
    }

    @Test
    public void staleActiveHitReturnsFalseAndKeepsThePart() {
        CacheManager manager = new CacheManager();
        PagePart stale = part(2, 3);
        manager.cachePart(stale);
        stale.markStale();

        assertFalse(manager.upPartIfContained(2, stale.getPageRelativeBounds(), 99));

        assertSame(stale, manager.debugActivePart(2, stale.getPageRelativeBounds()));
        assertEquals(Collections.singletonList(stale), manager.getPageParts());
        assertEquals(3, stale.getCacheOrder());
        verify(stale.getRenderedBitmap(), never()).recycle();
    }

    @Test
    public void passiveHitMovesToActiveWithTheRequestedOrder() {
        CacheManager manager = new CacheManager();
        PagePart hit = part(2, 3);
        manager.cachePart(hit);
        manager.makeANewSet();
        assertNull(manager.debugActivePart(2, hit.getPageRelativeBounds()));

        assertTrue(manager.upPartIfContained(2, hit.getPageRelativeBounds(), 99));

        assertSame(hit, manager.debugActivePart(2, hit.getPageRelativeBounds()));
        assertEquals(Collections.singletonList(hit), manager.getPageParts());
        assertEquals(99, hit.getCacheOrder());
        verify(hit.getRenderedBitmap(), never()).recycle();
    }

    @Test
    public void scalingKeepsActivePartsThroughFourTimesCapacityAndContractsAfterwards() {
        int original = Constants.Cache.CACHE_SIZE;
        Constants.Cache.CACHE_SIZE = 2;
        try {
            CacheManager manager = new CacheManager();
            manager.setScaling(true);
            PagePart[] parts = new PagePart[8];
            for (int i = 0; i < parts.length; i++) {
                parts[i] = part(i, i);
                manager.cachePart(parts[i]);
                if (i == 1) {
                    manager.makeANewSet();
                }
            }
            assertEquals(Arrays.asList(parts), manager.getPageParts());
            for (PagePart part : parts) {
                verify(part.getRenderedBitmap(), never()).recycle();
            }

            manager.setScaling(false);
            PagePart incoming = part(8, 8);
            manager.cachePart(incoming);

            assertEquals(Arrays.asList(parts[7], incoming), manager.getPageParts());
            for (int i = 0; i < 7; i++) {
                verify(parts[i].getRenderedBitmap(), times(1)).recycle();
            }
            verify(parts[7].getRenderedBitmap(), never()).recycle();
            verify(incoming.getRenderedBitmap(), never()).recycle();
        } finally {
            Constants.Cache.CACHE_SIZE = original;
        }
    }

    @Test
    public void scalingEvictsTheOldestPassivePartAtFourTimesCapacity() {
        int original = Constants.Cache.CACHE_SIZE;
        Constants.Cache.CACHE_SIZE = 1;
        try {
            CacheManager manager = new CacheManager();
            manager.setScaling(true);
            PagePart oldest = part(0, 0);
            PagePart newer = part(1, 1);
            manager.cachePart(newer);
            manager.cachePart(oldest);
            manager.makeANewSet();
            PagePart active = part(2, 2);
            PagePart next = part(3, 3);
            PagePart incoming = part(4, 4);
            manager.cachePart(active);
            manager.cachePart(next);
            assertEquals(4, manager.getPageParts().size());
            verify(oldest.getRenderedBitmap(), never()).recycle();

            manager.cachePart(incoming);

            assertEquals(Arrays.asList(newer, active, next, incoming), manager.getPageParts());
            verify(oldest.getRenderedBitmap(), times(1)).recycle();
            verify(newer.getRenderedBitmap(), never()).recycle();
            verify(active.getRenderedBitmap(), never()).recycle();
        } finally {
            Constants.Cache.CACHE_SIZE = original;
        }
    }

    @Test
    public void normalEvictionPrefersPassivePartsOverOlderActiveParts() {
        int original = Constants.Cache.CACHE_SIZE;
        Constants.Cache.CACHE_SIZE = 2;
        try {
            CacheManager manager = new CacheManager();
            PagePart passive = part(0, 99);
            PagePart active = part(1, 1);
            PagePart incoming = part(2, 2);
            manager.cachePart(passive);
            manager.makeANewSet();
            manager.cachePart(active);

            manager.cachePart(incoming);

            assertEquals(Arrays.asList(active, incoming), manager.getPageParts());
            verify(passive.getRenderedBitmap(), times(1)).recycle();
            verify(active.getRenderedBitmap(), never()).recycle();
        } finally {
            Constants.Cache.CACHE_SIZE = original;
        }
    }

    @Test
    public void thirdSnapshotKeepsOnlyTheNewestPreviousSnapshotForItsPage() {
        CacheManager manager = new CacheManager();
        PagePart oldest = snapshot(2, 1);
        PagePart newer = snapshot(2, 2);
        PagePart incoming = snapshot(2, 3);
        manager.cachePart(oldest);
        manager.makeANewSet();
        manager.cachePart(newer);

        manager.cachePart(incoming);

        assertEquals(Arrays.asList(newer, incoming), manager.getPageParts());
        verify(oldest.getRenderedBitmap(), times(1)).recycle();
        verify(newer.getRenderedBitmap(), never()).recycle();
    }

    @Test
    public void snapshotCapacityDropsTheOldestAcrossBothSetsBeforeAddingAnother() {
        int original = Constants.Cache.SNAPSHOTS_CACHE_SIZE;
        Constants.Cache.SNAPSHOTS_CACHE_SIZE = 8;
        try {
            CacheManager manager = new CacheManager();
            PagePart[] snapshots = new PagePart[9];
            for (int i = 0; i < 8; i++) {
                snapshots[i] = snapshot(i, i + 1);
                manager.cachePart(snapshots[i]);
                if (i == 3) {
                    manager.makeANewSet();
                }
            }
            assertEquals(8, manager.getPageParts().size());
            snapshots[8] = snapshot(8, 9);

            manager.cachePart(snapshots[8]);

            assertEquals(Arrays.asList(Arrays.copyOfRange(snapshots, 1, 9)), manager.getPageParts());
            verify(snapshots[0].getRenderedBitmap(), times(1)).recycle();
            for (int i = 1; i < snapshots.length; i++) {
                verify(snapshots[i].getRenderedBitmap(), never()).recycle();
            }
        } finally {
            Constants.Cache.SNAPSHOTS_CACHE_SIZE = original;
        }
    }

    @Test
    public void invalidationMarksOnlyTheMatchingPageInBothSets() {
        CacheManager manager = new CacheManager();
        PagePart passiveTarget = snapshot(2, 1);
        PagePart passiveOther = part(3, 2);
        PagePart activeTarget = snapshot(2, 3);
        PagePart activeOther = part(4, 4);
        manager.cachePart(passiveTarget);
        manager.cachePart(passiveOther);
        manager.makeANewSet();
        manager.cachePart(activeTarget);
        manager.cachePart(activeOther);

        manager.invalidatePageParts(2);

        assertTrue(passiveTarget.isStale());
        assertTrue(activeTarget.isStale());
        assertFalse(passiveOther.isStale());
        assertFalse(activeOther.isStale());
        assertEquals(Arrays.asList(passiveTarget, passiveOther, activeTarget, activeOther), manager.getPageParts());
    }

    @Ignore("Pending ruling: equal PageParts inherit identity hash codes, expected equal hash codes")
    @Test
    public void equalPagePartsHaveEqualHashCodes() {
        PagePart first = part(2, 1);
        PagePart second = part(2, 9);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Ignore("Pending ruling: comparing null bounds throws NullPointerException, expected false")
    @Test
    public void missingBoundsAreUnequalToPresentBounds() {
        PagePart missing = new PagePart(2, null, null, 0);
        PagePart present = part(2, 1);

        assertFalse(missing.equals(present));
        assertFalse(present.equals(missing));
    }

    private static PagePart snapshot(int page, int order) {
        PagePart part = part(page, order);
        part.getPageRelativeBounds().right = 1f / order;
        part.markSnapshot();
        return part;
    }

    private static PagePart part(int page, int order) {
        RectF bounds = new RectF();
        bounds.left = 0.1f;
        bounds.top = 0.2f;
        bounds.right = 0.9f;
        bounds.bottom = 0.8f;
        return new PagePart(page, mock(Bitmap.class), bounds, order);
    }
}
