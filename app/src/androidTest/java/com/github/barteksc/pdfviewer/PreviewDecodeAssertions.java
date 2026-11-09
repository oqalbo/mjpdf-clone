// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer;

import android.graphics.Bitmap;

import com.github.barteksc.pdfviewer.preview.PreviewStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class PreviewDecodeAssertions {
    public static void assertFailedReuseReleased(File root, int width, int height,
                                                byte[] payload, int attempts) throws IOException {
        AndroidPreviewBitmaps codec = new AndroidPreviewBitmaps(width);
        PreviewStore<Bitmap> store = new PreviewStore<>(codec, codec,
                page -> 0, page -> 0, page -> false, Runnable::run, Runnable::run,
                (long) width * height * 4, 96L * 1024 * 1024, 4);
        store.setBucket(width);
        codec.attachPool(store.getPool());
        String document = "reuse-failure-" + UUID.randomUUID();
        File directory = new File(root, document);
        store.attachDisk(root, document);
        try {
            for (int page = 0; page < attempts; page++) {
                Bitmap reuse = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
                try {
                    store.put(page, reuse, 0);
                    assertSame("the store owns the exact tracked bitmap before invalidation", reuse, store.peek(page));
                    File[] entries = directory.listFiles((parent, name) -> name.endsWith(".jpg"));
                    assertEquals("the real encoder wrote exactly one pending preview", 1, entries == null ? 0 : entries.length);
                    File entry = entries[0];
                    try (FileOutputStream output = new FileOutputStream(entry)) {
                        output.write(payload);
                    }
                    store.invalidatePage(page);
                    assertFalse("the valid tracked buffer is available for reuse", reuse.isRecycled());
                    AtomicInteger misses = new AtomicInteger();
                    store.requestDecode(page,
                            () -> { throw new AssertionError("invalid preview bytes must not publish a bitmap"); },
                            misses::incrementAndGet);
                    assertEquals("the failed decode invokes its real cache-miss callback", 1, misses.get());
                    assertTrue("the exact reuse bitmap acquired by the failed decode is recycled", reuse.isRecycled());
                    assertNull("a failed decode publishes no memory entry", store.peek(page));
                    assertFalse("the failed decode removes its damaged disk entry", entry.exists());
                } finally {
                    store.invalidatePage(page);
                    store.getPool().clear();
                    if (!reuse.isRecycled()) reuse.recycle();
                }
            }
        } finally {
            store.close();
        }
    }
}
