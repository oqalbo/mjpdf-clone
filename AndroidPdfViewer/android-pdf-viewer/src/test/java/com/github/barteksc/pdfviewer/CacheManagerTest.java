package com.github.barteksc.pdfviewer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import com.github.barteksc.pdfviewer.model.PagePart;
import com.github.barteksc.pdfviewer.util.Constants;

import org.junit.Test;

public class CacheManagerTest {

    @Test
    public void debugPeakTracksDisjointScalingSetsAndResetStartsANewObservation() {
        int originalCacheSize = Constants.Cache.CACHE_SIZE;
        Constants.Cache.CACHE_SIZE = 2;
        try {
            CacheManager manager = new CacheManager();
            manager.cachePart(part());
            manager.cachePart(part());

            long observation = manager.debugResetPartPeak();
            assertPeak(manager.debugPartPeakStats(), observation, 0, 0, 0, 0, 0, 0);

            manager.makeANewSet();
            manager.setScaling(true);
            manager.cachePart(part());

            long[] expanded = manager.debugPartPeakStats();
            assertPeak(expanded, observation, 1, 2, 1, 2, 3, 1);
            assertTrue("the recorded peak must exceed the normal inclusive bound", expanded[5] > 2);

            manager.setScaling(false);
            manager.cachePart(part());
            long[] resting = manager.debugPartStats();
            assertTrue("normal insertion must contract the combined cache to its inclusive bound",
                    resting[0] + resting[1] <= 2);

            long nextObservation = manager.debugResetPartPeak();
            assertEquals(observation + 1, nextObservation);
            assertPeak(manager.debugPartPeakStats(), nextObservation, 0, 0, 0, 0, 0, 0);
        } finally {
            Constants.Cache.CACHE_SIZE = originalCacheSize;
        }
    }

    private static PagePart part() {
        return mock(PagePart.class);
    }

    private static void assertPeak(long[] actual, long observation, int active, int passive,
                                   int activeTiles, int passiveTiles, int total, int insertions) {
        assertEquals(observation, actual[0]);
        assertEquals(active, actual[1]);
        assertEquals(passive, actual[2]);
        assertEquals(activeTiles, actual[3]);
        assertEquals(passiveTiles, actual[4]);
        assertEquals(total, actual[5]);
        assertEquals(insertions, actual[6]);
    }
}
