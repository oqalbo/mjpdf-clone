// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer.preview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.github.barteksc.pdfviewer.preview.PreviewTestSupport.FakeAdapter;
import com.github.barteksc.pdfviewer.preview.PreviewTestSupport.FakeBitmap;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class PreviewMemoryCacheTest {

    private static final class RecordingListener implements PreviewMemoryCache.Listener<FakeBitmap> {
        final List<PreviewKey> keys = new ArrayList<>();
        final List<FakeBitmap> bitmaps = new ArrayList<>();

        @Override
        public void onReleased(PreviewKey key, FakeBitmap bitmap) {
            keys.add(key);
            bitmaps.add(bitmap);
        }
    }

    @Test
    public void evictionByByteBudget_releasesEldestToListener() {
        RecordingListener listener = new RecordingListener();
        PreviewMemoryCache<FakeBitmap> cache = new PreviewMemoryCache<>(250, new FakeAdapter(), listener);

        PreviewKey k0 = new PreviewKey(0, 512, 0);
        PreviewKey k1 = new PreviewKey(1, 512, 0);
        PreviewKey k2 = new PreviewKey(2, 512, 0);
        FakeBitmap b0 = new FakeBitmap(0, 100);
        FakeBitmap b1 = new FakeBitmap(1, 100);
        FakeBitmap b2 = new FakeBitmap(2, 100);

        cache.put(k0, b0);
        cache.put(k1, b1);
        cache.put(k2, b2);

        assertEquals(1, listener.keys.size());
        assertEquals(k0, listener.keys.get(0));
        assertSame(b0, listener.bitmaps.get(0));
        assertNull(cache.get(k0));
        assertNotNull(cache.get(k1));
        assertNotNull(cache.get(k2));
        assertEquals(200, cache.sizeBytes());
    }

    @Test
    public void accessOrder_protectsRecentlyReadEntryFromEviction() {
        RecordingListener listener = new RecordingListener();
        PreviewMemoryCache<FakeBitmap> cache = new PreviewMemoryCache<>(250, new FakeAdapter(), listener);

        PreviewKey k0 = new PreviewKey(0, 512, 0);
        PreviewKey k1 = new PreviewKey(1, 512, 0);
        PreviewKey k2 = new PreviewKey(2, 512, 0);
        cache.put(k0, new FakeBitmap(0, 100));
        cache.put(k1, new FakeBitmap(1, 100));

        cache.get(k0);
        cache.put(k2, new FakeBitmap(2, 100));

        assertNotNull(cache.get(k0));
        assertNull(cache.get(k1));
    }

    @Test
    public void put_replacingKeyReleasesPreviousBitmap() {
        RecordingListener listener = new RecordingListener();
        PreviewMemoryCache<FakeBitmap> cache = new PreviewMemoryCache<>(1000, new FakeAdapter(), listener);

        PreviewKey key = new PreviewKey(0, 512, 0);
        FakeBitmap first = new FakeBitmap(0, 100);
        FakeBitmap second = new FakeBitmap(1, 140);
        cache.put(key, first);
        cache.put(key, second);

        assertSame(first, listener.bitmaps.get(0));
        assertSame(second, cache.get(key));
        assertEquals(140, cache.sizeBytes());
    }

    @Test
    public void removePage_dropsAllTagsAndBucketsForPage() {
        RecordingListener listener = new RecordingListener();
        PreviewMemoryCache<FakeBitmap> cache = new PreviewMemoryCache<>(1000, new FakeAdapter(), listener);

        cache.put(new PreviewKey(5, 512, 0), new FakeBitmap(0, 100));
        cache.put(new PreviewKey(5, 256, 3), new FakeBitmap(1, 100));
        cache.put(new PreviewKey(6, 512, 0), new FakeBitmap(2, 100));

        cache.removePage(5);

        assertNull(cache.get(new PreviewKey(5, 512, 0)));
        assertNull(cache.get(new PreviewKey(5, 256, 3)));
        assertNotNull(cache.get(new PreviewKey(6, 512, 0)));
        assertEquals(2, listener.keys.size());
        assertEquals(100, cache.sizeBytes());
    }

    @Test
    public void clear_releasesEverything() {
        RecordingListener listener = new RecordingListener();
        PreviewMemoryCache<FakeBitmap> cache = new PreviewMemoryCache<>(1000, new FakeAdapter(), listener);
        cache.put(new PreviewKey(0, 512, 0), new FakeBitmap(0, 100));
        cache.put(new PreviewKey(1, 512, 0), new FakeBitmap(1, 100));

        cache.clear();

        assertEquals(2, listener.keys.size());
        assertEquals(0, cache.sizeBytes());
        assertEquals(0, cache.count());
    }

    @Test
    public void evictedBitmapReachesPoolWhenListenerRoutesToPool() {
        final PreviewBitmapPool<FakeBitmap> pool = new PreviewBitmapPool<>(new FakeAdapter(), 4);
        PreviewMemoryCache.Listener<FakeBitmap> listener = new PreviewMemoryCache.Listener<FakeBitmap>() {
            @Override
            public void onReleased(PreviewKey key, FakeBitmap bitmap) {
                pool.release(key.bucketWidthPx, bitmap);
            }
        };
        PreviewMemoryCache<FakeBitmap> cache = new PreviewMemoryCache<>(150, new FakeAdapter(), listener);

        FakeBitmap b0 = new FakeBitmap(0, 100);
        cache.put(new PreviewKey(0, 512, 0), b0);
        cache.put(new PreviewKey(1, 512, 0), new FakeBitmap(1, 100));

        assertSame(b0, pool.acquire(512));
    }
}
