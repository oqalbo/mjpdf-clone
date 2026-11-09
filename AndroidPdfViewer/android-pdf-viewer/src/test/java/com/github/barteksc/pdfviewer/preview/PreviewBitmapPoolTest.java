// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer.preview;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.github.barteksc.pdfviewer.preview.PreviewTestSupport.FakeAdapter;
import com.github.barteksc.pdfviewer.preview.PreviewTestSupport.FakeBitmap;

import org.junit.Test;

public class PreviewBitmapPoolTest {

    @Test
    public void acquireEmpty_returnsNull() {
        PreviewBitmapPool<FakeBitmap> pool = new PreviewBitmapPool<>(new FakeAdapter(), 2);
        assertNull(pool.acquire(512));
    }

    @Test
    public void releaseThenAcquire_returnsSameBitmap() {
        PreviewBitmapPool<FakeBitmap> pool = new PreviewBitmapPool<>(new FakeAdapter(), 2);
        FakeBitmap bitmap = new FakeBitmap(1, 100);
        pool.release(512, bitmap);
        assertSame(bitmap, pool.acquire(512));
    }

    @Test
    public void acquireDifferentBucket_missesOtherBucketEntries() {
        PreviewBitmapPool<FakeBitmap> pool = new PreviewBitmapPool<>(new FakeAdapter(), 2);
        FakeBitmap bitmap = new FakeBitmap(1, 100);
        pool.release(512, bitmap);
        assertNull(pool.acquire(256));
        assertSame(bitmap, pool.acquire(512));
    }

    @Test
    public void releaseBeyondCap_recyclesOverflow() {
        FakeAdapter adapter = new FakeAdapter();
        PreviewBitmapPool<FakeBitmap> pool = new PreviewBitmapPool<>(adapter, 2);
        FakeBitmap first = new FakeBitmap(1, 100);
        FakeBitmap second = new FakeBitmap(2, 100);
        FakeBitmap overflow = new FakeBitmap(3, 100);
        pool.release(512, first);
        pool.release(512, second);
        pool.release(512, overflow);

        assertTrue(overflow.recycled);
        assertFalse(first.recycled);
        assertFalse(second.recycled);
    }

    @Test
    public void clear_recyclesPooledBitmaps() {
        FakeAdapter adapter = new FakeAdapter();
        PreviewBitmapPool<FakeBitmap> pool = new PreviewBitmapPool<>(adapter, 4);
        FakeBitmap bitmap = new FakeBitmap(1, 100);
        pool.release(512, bitmap);
        pool.clear();

        assertTrue(bitmap.recycled);
        assertNull(pool.acquire(512));
    }
}
