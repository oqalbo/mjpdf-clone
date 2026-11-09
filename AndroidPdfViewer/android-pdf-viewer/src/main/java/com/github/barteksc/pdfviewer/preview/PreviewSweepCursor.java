// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer.preview;

import java.util.Arrays;

public final class PreviewSweepCursor {

    public interface Coverage {

        boolean covered(int page);

        boolean pending(int page);
    }

    private boolean[] confirmed;

    public synchronized void ensureCapacity(int pageCount) {
        if (pageCount <= 0) {
            confirmed = null;
            return;
        }
        if (confirmed == null || confirmed.length != pageCount) {
            confirmed = new boolean[pageCount];
        }
    }

    public synchronized void reset(int page) {
        if (confirmed != null && page >= 0 && page < confirmed.length) {
            confirmed[page] = false;
        }
    }

    public synchronized void resetAll() {
        if (confirmed != null) {
            Arrays.fill(confirmed, false);
        }
    }

    public synchronized int nextPage(int currentPage, int direction, Coverage coverage) {
        if (confirmed == null) {
            return -1;
        }
        int step = direction >= 0 ? 1 : -1;
        int count = confirmed.length;
        for (int radius = 0; radius < count; radius++) {
            int primary = check(currentPage + step * radius, coverage);
            if (primary >= 0) {
                return primary;
            }
            if (radius != 0) {
                int secondary = check(currentPage - step * radius, coverage);
                if (secondary >= 0) {
                    return secondary;
                }
            }
        }
        return -1;
    }

    private int check(int page, Coverage coverage) {
        if (page < 0 || page >= confirmed.length || confirmed[page]) {
            return -1;
        }
        if (coverage.pending(page)) {
            return -1;
        }
        if (coverage.covered(page)) {
            confirmed[page] = true;
            return -1;
        }
        return page;
    }
}
