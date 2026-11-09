// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer.preview;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class PreviewSweepCursorTest {

    private static final class FakeCoverage implements PreviewSweepCursor.Coverage {

        final Set<Integer> covered = new HashSet<>();
        final Set<Integer> pending = new HashSet<>();
        int coveredQueries = 0;

        @Override
        public boolean covered(int page) {
            coveredQueries++;
            return covered.contains(page);
        }

        @Override
        public boolean pending(int page) {
            return pending.contains(page);
        }
    }

    @Test
    public void nearestUncovered_biasedForward() {
        PreviewSweepCursor cursor = new PreviewSweepCursor();
        cursor.ensureCapacity(10);
        FakeCoverage coverage = new FakeCoverage();
        coverage.covered.add(5);

        assertEquals(6, cursor.nextPage(5, 1, coverage));
    }

    @Test
    public void nearestUncovered_biasedBackward() {
        PreviewSweepCursor cursor = new PreviewSweepCursor();
        cursor.ensureCapacity(10);
        FakeCoverage coverage = new FakeCoverage();
        coverage.covered.add(5);

        assertEquals(4, cursor.nextPage(5, -1, coverage));
    }

    @Test
    public void pendingPage_skippedButNotConfirmed() {
        PreviewSweepCursor cursor = new PreviewSweepCursor();
        cursor.ensureCapacity(10);
        FakeCoverage coverage = new FakeCoverage();
        coverage.pending.add(5);

        assertEquals(6, cursor.nextPage(5, 1, coverage));

        coverage.pending.remove(5);
        assertEquals(5, cursor.nextPage(5, 1, coverage));
    }

    @Test
    public void coveredPages_confirmedAndNotRequeried() {
        PreviewSweepCursor cursor = new PreviewSweepCursor();
        cursor.ensureCapacity(4);
        FakeCoverage coverage = new FakeCoverage();
        coverage.covered.add(0);
        coverage.covered.add(1);
        coverage.covered.add(2);

        assertEquals(3, cursor.nextPage(0, 1, coverage));
        int firstPass = coverage.coveredQueries;

        coverage.covered.add(3);
        assertEquals(-1, cursor.nextPage(0, 1, coverage));
        assertEquals(firstPass + 1, coverage.coveredQueries);
    }

    @Test
    public void reset_reenablesConfirmedPage() {
        PreviewSweepCursor cursor = new PreviewSweepCursor();
        cursor.ensureCapacity(4);
        FakeCoverage coverage = new FakeCoverage();
        coverage.covered.add(0);
        coverage.covered.add(1);
        coverage.covered.add(2);
        coverage.covered.add(3);

        assertEquals(-1, cursor.nextPage(0, 1, coverage));

        coverage.covered.remove(2);
        cursor.reset(2);
        assertEquals(2, cursor.nextPage(0, 1, coverage));
    }

    @Test
    public void resetAll_reenablesEveryPage() {
        PreviewSweepCursor cursor = new PreviewSweepCursor();
        cursor.ensureCapacity(3);
        FakeCoverage coverage = new FakeCoverage();
        coverage.covered.add(0);
        coverage.covered.add(1);
        coverage.covered.add(2);

        assertEquals(-1, cursor.nextPage(0, 1, coverage));

        coverage.covered.clear();
        cursor.resetAll();
        assertEquals(0, cursor.nextPage(0, 1, coverage));
    }
}
