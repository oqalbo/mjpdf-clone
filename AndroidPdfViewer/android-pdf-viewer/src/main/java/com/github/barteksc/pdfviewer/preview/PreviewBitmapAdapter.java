// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer.preview;

public interface PreviewBitmapAdapter<T> {

    int byteCount(T bitmap);

    void recycle(T bitmap);
}
