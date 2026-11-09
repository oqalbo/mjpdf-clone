// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer.preview;

final class PreviewKey {

    final int page;
    final int bucketWidthPx;
    final int tag;

    PreviewKey(int page, int bucketWidthPx, int tag) {
        this.page = page;
        this.bucketWidthPx = bucketWidthPx;
        this.tag = tag;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PreviewKey)) {
            return false;
        }
        PreviewKey that = (PreviewKey) other;
        return page == that.page && bucketWidthPx == that.bucketWidthPx && tag == that.tag;
    }

    @Override
    public int hashCode() {
        int result = page;
        result = 31 * result + bucketWidthPx;
        result = 31 * result + tag;
        return result;
    }

    @Override
    public String toString() {
        return "p" + page + "-w" + bucketWidthPx + "-t" + tag;
    }
}
