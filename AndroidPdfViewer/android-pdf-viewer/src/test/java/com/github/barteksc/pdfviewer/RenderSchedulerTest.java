// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer;

import static com.github.barteksc.pdfviewer.RenderTestSupport.tile;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.github.barteksc.pdfviewer.RenderTestSupport.FakeGenerations;
import com.github.barteksc.pdfviewer.exception.PageRenderingException;
import com.github.barteksc.pdfviewer.model.PagePart;

import org.junit.Test;

public class RenderSchedulerTest {

    @Test
    public void fenceAbort_resubmitsOnceThenDelivers() {
        RenderQueue queue = new RenderQueue(new FakeGenerations());

        final int[] executions = {0};
        RenderScheduler.RenderExecutor executor = new RenderScheduler.RenderExecutor() {
            @Override
            public RenderScheduler.RenderResult execute(RenderTask task) {
                executions[0]++;
                if (executions[0] == 1) {
                    return RenderScheduler.RenderResult.aborted();
                }
                return RenderScheduler.RenderResult.delivered(new PagePart(task.page, null, null, 0));
            }
        };

        final int[] deliveries = {0};
        RenderScheduler.ResultSink sink = new RenderScheduler.ResultSink() {
            @Override
            public void deliver(PagePart part) {
                deliveries[0]++;
            }

            @Override
            public void error(PageRenderingException ex) {
            }
        };

        RenderScheduler scheduler = new RenderScheduler(queue, executor, sink);
        scheduler.runningForTest(true);

        RenderTask task = tile(4, RenderTask.P0);
        queue.beginWave(RenderQueue.WaveKind.LOAD);
        queue.submit(task);
        queue.endWave();

        RenderTask first = queue.pollNextNow();
        assertSame(task, first);
        scheduler.runTask(first);

        RenderTask second = queue.pollNextNow();
        assertNotNull(second);
        assertNotSame(task, second);
        scheduler.runTask(second);

        assertEquals(2, executions[0]);
        assertEquals(1, deliveries[0]);
    }

    @Test
    public void queueCancelledAbort_isNotResubmitted() {
        RenderQueue queue = new RenderQueue(new FakeGenerations());

        final int[] executions = {0};
        RenderScheduler.RenderExecutor executor = new RenderScheduler.RenderExecutor() {
            @Override
            public RenderScheduler.RenderResult execute(RenderTask task) {
                executions[0]++;
                return RenderScheduler.RenderResult.aborted();
            }
        };

        RenderScheduler.ResultSink sink = new RenderScheduler.ResultSink() {
            @Override
            public void deliver(PagePart part) {
            }

            @Override
            public void error(PageRenderingException ex) {
            }
        };

        RenderScheduler scheduler = new RenderScheduler(queue, executor, sink);
        scheduler.runningForTest(true);

        RenderTask task = tile(4, RenderTask.P0);
        queue.beginWave(RenderQueue.WaveKind.LOAD);
        queue.submit(task);
        queue.endWave();

        RenderTask first = queue.pollNextNow();
        assertSame(task, first);
        queue.cancelPage(4);
        scheduler.runTask(first);

        assertEquals(1, executions[0]);
        assertNull(queue.pollNextNow());
    }
}
