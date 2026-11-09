// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer.preview;

public interface PreviewCodec<T> {

    byte[] encode(T bitmap);

    T decode(byte[] data);
}
