// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer.util;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public class UtilStreamTest {

    @Test
    public void streamLargerThanTwoBuffersPreservesEveryByteAndTheTail() throws IOException {
        byte[] bytes = bytes(2 * 4096 + 137);

        assertArrayEquals(bytes, Util.toByteArray(new ByteArrayInputStream(bytes)));
    }

    @Test
    public void shortReadsContinueUntilEndOfStream() throws IOException {
        byte[] bytes = bytes(5003);
        ByteArrayInputStream stream = new ByteArrayInputStream(bytes) {
            @Override
            public synchronized int read(byte[] buffer, int offset, int length) {
                return super.read(buffer, offset, Math.min(length, 7));
            }
        };

        assertArrayEquals(bytes, Util.toByteArray(stream));
    }

    private static byte[] bytes(int size) {
        byte[] bytes = new byte[size];
        for (int i = 0; i < size; i++) {
            bytes[i] = (byte) (i * 31 + i / 256);
        }
        return bytes;
    }
}
