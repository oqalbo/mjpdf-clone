// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Ignore;
import org.junit.Test;

public class RenderTaskEquivalenceTest {

    @Ignore("Pending ruling: annotation rendering is ignored by equivalence, expected different tasks")
    @Test
    public void annotationRenderingChangeRequiresADifferentTask() {
        assertFalse(RenderTestSupport.tile(2, RenderTask.P0).equivalentTo(tile(false, false, true)));
    }

    @Ignore("Pending ruling: best quality is ignored by equivalence, expected different tasks")
    @Test
    public void bestQualityChangeRequiresADifferentTask() {
        assertFalse(RenderTestSupport.tile(2, RenderTask.P0).equivalentTo(tile(false, true, false)));
    }

    @Ignore("Pending ruling: snapshot is ignored by equivalence, expected different tasks")
    @Test
    public void snapshotChangeRequiresADifferentTask() {
        assertFalse(RenderTestSupport.tile(2, RenderTask.P0).equivalentTo(tile(true, false, false)));
    }

    @Test
    public void annotationRenderingChangeCurrentlyAdoptsTheInFlightTask() {
        assertAdoptsInFlight(tile(false, false, true));
    }

    @Test
    public void bestQualityChangeCurrentlyAdoptsTheInFlightTask() {
        assertAdoptsInFlight(tile(false, true, false));
    }

    @Test
    public void snapshotChangeCurrentlyAdoptsTheInFlightTask() {
        assertAdoptsInFlight(tile(true, false, false));
    }

    private static void assertAdoptsInFlight(RenderTask incoming) {
        RenderQueue queue = new RenderQueue(new RenderTestSupport.FakeGenerations());
        RenderTask first = RenderTestSupport.tile(2, RenderTask.P0);
        queue.beginWave(RenderQueue.WaveKind.LOAD);
        queue.submit(first);
        queue.endWave();
        assertSame(first, queue.pollNextNow());
        long firstWave = first.waveId;

        queue.beginWave(RenderQueue.WaveKind.LOAD);
        queue.submit(incoming);
        queue.endWave();

        assertEquals(firstWave + 1, first.waveId);
        assertFalse(first.isQueueCancelled());
        assertEquals(1, queue.debugPendingTiles());
        assertNull(queue.pollNextNow());
        queue.completed(first);
        assertEquals(0, queue.debugPendingTiles());
        assertNull(queue.pollNextNow());
    }

    private static RenderTask tile(boolean snapshot, boolean bestQuality, boolean annotationRendering) {
        return RenderTask.tile(2, 0f, 0f, 1f, 1f, 10f, 10f,
                snapshot, 0, bestQuality, annotationRendering);
    }
}
