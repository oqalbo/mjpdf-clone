// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer;

import static com.github.barteksc.pdfviewer.RenderTestSupport.preview;
import static com.github.barteksc.pdfviewer.RenderTestSupport.tile;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import com.github.barteksc.pdfviewer.RenderTestSupport.FakeGenerations;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RenderQueueTest {

    private RenderQueue queue(FakeGenerations generations) {
        return new RenderQueue(generations);
    }

    @Test
    public void ordering_priorityThenSeq() {
        RenderQueue queue = queue(new FakeGenerations());
        RenderTask t1 = tile(1, RenderTask.P1);
        RenderTask t2 = tile(2, RenderTask.P0);
        RenderTask t3 = tile(3, RenderTask.P2);
        RenderTask t4 = tile(4, RenderTask.P0);
        RenderTask t5 = tile(5, RenderTask.P1);
        RenderTask t6 = tile(6, RenderTask.P2);

        queue.beginWave(RenderQueue.WaveKind.LOAD);
        queue.submit(t1);
        queue.submit(t2);
        queue.submit(t3);
        queue.submit(t4);
        queue.submit(t5);
        queue.submit(t6);
        queue.endWave();

        List<RenderTask> order = new ArrayList<>();
        RenderTask next;
        while ((next = queue.pollNextNow()) != null) {
            order.add(next);
            queue.completed(next);
        }

        assertEquals(Arrays.asList(t2, t4, t1, t5, t3, t6), order);
    }

    @Test
    public void waveStorm_adoptsInFlightWithoutCancelOrDuplication() {
        RenderQueue queue = queue(new FakeGenerations());
        RenderTask a = tile(5, RenderTask.P0);
        queue.beginWave(RenderQueue.WaveKind.LOAD);
        queue.submit(a);
        queue.endWave();

        assertSame(a, queue.pollNextNow());

        for (int i = 0; i < 50; i++) {
            queue.beginWave(RenderQueue.WaveKind.LOAD);
            queue.submit(a.copyForResubmit());
            queue.endWave();
        }

        assertFalse(a.cancel.get());
        assertNull(queue.pollNextNow());
    }

    @Test
    public void orphan_cancelledWhenWaveDiesWithoutResubmission() {
        RenderQueue queue = queue(new FakeGenerations());
        RenderTask a = tile(3, RenderTask.P0);
        queue.beginWave(RenderQueue.WaveKind.LOAD);
        queue.submit(a);
        queue.endWave();

        assertSame(a, queue.pollNextNow());

        queue.beginWave(RenderQueue.WaveKind.LOAD);
        queue.endWave();

        assertTrue(a.cancel.get());
    }

    @Test
    public void deadWave_queuedTaskNeverReturned() {
        RenderQueue queue = queue(new FakeGenerations());
        RenderTask a = tile(2, RenderTask.P0);
        queue.beginWave(RenderQueue.WaveKind.LOAD);
        queue.submit(a);
        queue.endWave();

        queue.beginWave(RenderQueue.WaveKind.LOAD);
        queue.endWave();

        assertNull(queue.pollNextNow());
    }

    @Test
    public void generationBump_dropsStaleTask() {
        FakeGenerations generations = new FakeGenerations();
        RenderQueue queue = queue(generations);
        RenderTask a = tile(7, RenderTask.P0);
        queue.beginWave(RenderQueue.WaveKind.LOAD);
        queue.submit(a);
        queue.endWave();

        generations.bump(7);

        assertNull(queue.pollNextNow());
    }

    @Test
    public void cancelPage_cancelsOnlyMatchingInFlight() {
        RenderQueue queue = queue(new FakeGenerations());
        RenderTask a = tile(1, RenderTask.P0);
        RenderTask b = tile(2, RenderTask.P0);
        queue.beginWave(RenderQueue.WaveKind.LOAD);
        queue.submit(a);
        queue.submit(b);
        queue.endWave();

        assertSame(a, queue.pollNextNow());
        queue.cancelPage(1);
        assertTrue(a.cancel.get());
        assertFalse(b.cancel.get());

        queue.completed(a);
        assertSame(b, queue.pollNextNow());
        queue.cancelPage(1);
        assertFalse(b.cancel.get());
    }

    @Test
    public void interactionActive_gatesP2WhileP0QueuedOrInteracting() {
        RenderQueue queue = queue(new FakeGenerations());
        RenderTask p2 = tile(1, RenderTask.P2);
        RenderTask p0 = tile(2, RenderTask.P0);
        queue.beginWave(RenderQueue.WaveKind.LOAD);
        queue.submit(p2);
        queue.submit(p0);
        queue.endWave();

        assertSame(p0, queue.pollNextNow());
        queue.completed(p0);

        queue.setInteractionActive(true);
        assertNull(queue.pollNextNow());

        queue.setInteractionActive(false);
        assertSame(p2, queue.pollNextNow());
    }

    @Test
    public void submitP0_cancelsInFlightP2Sweep() {
        RenderQueue queue = queue(new FakeGenerations());
        RenderTask sweep = preview(5, RenderTask.P2);
        queue.beginWave(RenderQueue.WaveKind.LOAD);
        queue.submit(sweep);
        queue.endWave();

        assertSame(sweep, queue.pollNextNow());

        queue.beginWave(RenderQueue.WaveKind.LOAD);
        queue.submit(tile(9, RenderTask.P0));
        assertTrue(sweep.cancel.get());
        queue.endWave();
    }

    @Test
    public void idleProducer_consultedWhenIdle_dispatchesAsP2() {
        RenderQueue queue = queue(new FakeGenerations());
        final RenderTask produced = preview(3, RenderTask.P2);
        final int[] calls = {0};
        queue.setIdleProducer(new RenderQueue.IdleProducer() {
            @Override
            public RenderTask produce() {
                calls[0]++;
                return calls[0] == 1 ? produced : null;
            }
        });

        RenderTask next = queue.pollNextNow();
        assertSame(produced, next);
        assertEquals(RenderTask.P2, next.priorityClass);
        assertEquals(1, calls[0]);
    }

    @Test
    public void idleProducer_notConsultedWhileInteractionActive() {
        RenderQueue queue = queue(new FakeGenerations());
        final int[] calls = {0};
        queue.setIdleProducer(new RenderQueue.IdleProducer() {
            @Override
            public RenderTask produce() {
                calls[0]++;
                return preview(1, RenderTask.P2);
            }
        });

        queue.setInteractionActive(true);
        assertNull(queue.pollNextNow());
        assertEquals(0, calls[0]);
    }

    @Test
    public void idleProducer_notConsultedWhileFlinging() {
        RenderQueue queue = queue(new FakeGenerations());
        final int[] calls = {0};
        queue.setIdleProducer(new RenderQueue.IdleProducer() {
            @Override
            public RenderTask produce() {
                calls[0]++;
                return preview(1, RenderTask.P2);
            }
        });

        queue.setFlinging(true);
        assertNull(queue.pollNextNow());
        assertEquals(0, calls[0]);
    }

    @Test
    public void idleProducer_resumesAfterFlingEnds() {
        RenderQueue queue = queue(new FakeGenerations());
        final RenderTask produced = preview(7, RenderTask.P2);
        queue.setIdleProducer(new RenderQueue.IdleProducer() {
            @Override
            public RenderTask produce() {
                return produced;
            }
        });

        queue.setFlinging(true);
        assertNull(queue.pollNextNow());

        queue.setFlinging(false);
        assertSame(produced, queue.pollNextNow());
    }

    @Test
    public void stop_drainsQueueAndCancelsInFlight() {
        RenderQueue queue = queue(new FakeGenerations());
        RenderTask a = tile(1, RenderTask.P0);
        RenderTask b = tile(2, RenderTask.P0);
        queue.beginWave(RenderQueue.WaveKind.LOAD);
        queue.submit(a);
        queue.submit(b);
        queue.endWave();

        assertSame(a, queue.pollNextNow());
        queue.stop();

        assertTrue(a.cancel.get());
        assertNull(queue.pollNextNow());
    }
}
