// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer.preview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.github.barteksc.pdfviewer.preview.PreviewTestSupport.FakeAdapter;
import com.github.barteksc.pdfviewer.preview.PreviewTestSupport.FakeBitmap;
import com.github.barteksc.pdfviewer.preview.PreviewTestSupport.FakeCodec;
import com.github.barteksc.pdfviewer.preview.PreviewTestSupport.FakeGenerations;
import com.github.barteksc.pdfviewer.preview.PreviewTestSupport.FakeTags;
import com.github.barteksc.pdfviewer.preview.PreviewTestSupport.FakeTransient;
import com.github.barteksc.pdfviewer.preview.PreviewTestSupport.Flag;
import com.github.barteksc.pdfviewer.preview.PreviewTestSupport.ManualExecutor;
import com.github.barteksc.pdfviewer.preview.PreviewTestSupport.SameThreadExecutor;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.concurrent.Executor;

public class PreviewStoreTest {

    private static final int BUCKET = 100;
    private static final long BIG = 64L * 1024 * 1024;
    private static final int POOL_CAP = 4;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final class Harness {
        final FakeAdapter adapter = new FakeAdapter();
        final FakeCodec codec = new FakeCodec();
        final FakeGenerations gens = new FakeGenerations();
        final FakeTags tags = new FakeTags();
        final FakeTransient transientFilter = new FakeTransient();
        final PreviewStore<FakeBitmap> store;
        final PreviewDiskCache seeder;

        Harness(Executor encoder, Executor decoder) {
            store = new PreviewStore<>(adapter, codec, gens, tags, transientFilter,
                    encoder, decoder, BIG, BIG, POOL_CAP);
            store.setBucket(BUCKET);
            seeder = new PreviewDiskCache(folder.getRoot());
        }
    }

    private static PreviewKey key(int page, int tag) {
        return new PreviewKey(page, BUCKET, tag);
    }

    @Test
    public void peek_usesCurrentTag() {
        Harness h = new Harness(new SameThreadExecutor(), new SameThreadExecutor());
        FakeBitmap bitmap = new FakeBitmap(1, 100);
        h.store.put(0, bitmap, 0);

        assertSame(bitmap, h.store.peek(0));
        h.tags.set(0, 3);
        assertNull(h.store.peek(0));
        h.tags.set(0, 0);
        assertSame(bitmap, h.store.peek(0));
    }

    @Test
    public void requestDecode_memoryHitIsNoOp() {
        Harness h = new Harness(new SameThreadExecutor(), new SameThreadExecutor());
        h.store.attachDisk(folder.getRoot(), "doc");
        h.store.put(0, new FakeBitmap(1, 100), 0);

        Flag callback = new Flag();
        h.store.requestDecode(0, callback);

        assertFalse(callback.ran);
        assertEquals(0, h.codec.decodeCalls);
    }

    @Test
    public void put_schedulesDiskWriteRunOnEncoder() {
        ManualExecutor encoder = new ManualExecutor();
        Harness h = new Harness(encoder, new SameThreadExecutor());
        h.store.attachDisk(folder.getRoot(), "doc");
        encoder.runAll();

        h.store.put(0, new FakeBitmap(1, 100), 0);
        assertFalse(h.seeder.exists("doc", key(0, 0)));

        encoder.runAll();
        assertTrue(h.seeder.exists("doc", key(0, 0)));
    }

    @Test
    public void put_generationBumpBeforeEncodeSkipsWrite() {
        ManualExecutor encoder = new ManualExecutor();
        Harness h = new Harness(encoder, new SameThreadExecutor());
        h.store.attachDisk(folder.getRoot(), "doc");
        encoder.runAll();

        h.store.put(0, new FakeBitmap(1, 100), 0);
        h.gens.bump(0);
        encoder.runAll();

        assertFalse(h.seeder.exists("doc", key(0, 0)));
    }

    @Test
    public void put_transientAtEncodeTimeSkipsWrite() {
        ManualExecutor encoder = new ManualExecutor();
        Harness h = new Harness(encoder, new SameThreadExecutor());
        h.store.attachDisk(folder.getRoot(), "doc");
        encoder.runAll();

        h.store.put(0, new FakeBitmap(1, 100), 0);
        h.transientFilter.set(0, true);
        encoder.runAll();

        assertFalse(h.seeder.exists("doc", key(0, 0)));
    }

    @Test
    public void put_transientAtPutTimeNeverSchedulesWrite() {
        ManualExecutor encoder = new ManualExecutor();
        Harness h = new Harness(encoder, new SameThreadExecutor());
        h.store.attachDisk(folder.getRoot(), "doc");
        encoder.runAll();

        h.transientFilter.set(0, true);
        h.store.put(0, new FakeBitmap(1, 100), 0);

        assertEquals(0, encoder.size());
    }

    @Test
    public void put_detachBeforeEncodeSkipsWrite() {
        ManualExecutor encoder = new ManualExecutor();
        Harness h = new Harness(encoder, new SameThreadExecutor());
        h.store.attachDisk(folder.getRoot(), "doc");
        encoder.runAll();

        h.store.put(0, new FakeBitmap(1, 100), 0);
        h.store.detachDisk();
        encoder.runAll();

        assertFalse(h.seeder.exists("doc", key(0, 0)));
    }

    @Test
    public void attachFlush_writesExistingEntriesAndAppliesGuards() {
        ManualExecutor encoder = new ManualExecutor();
        Harness h = new Harness(encoder, new SameThreadExecutor());

        h.store.put(0, new FakeBitmap(0, 100), 0);
        h.store.put(1, new FakeBitmap(1, 100), 0);
        h.store.put(2, new FakeBitmap(2, 100), 0);
        assertEquals(0, encoder.size());

        h.store.attachDisk(folder.getRoot(), "doc");
        h.gens.bump(1);
        h.transientFilter.set(2, true);
        encoder.runAll();

        assertTrue(h.seeder.exists("doc", key(0, 0)));
        assertFalse(h.seeder.exists("doc", key(1, 0)));
        assertFalse(h.seeder.exists("doc", key(2, 0)));
    }

    @Test
    public void attachFlush_detachBeforeFlushRunsSkipsEverything() {
        ManualExecutor encoder = new ManualExecutor();
        Harness h = new Harness(encoder, new SameThreadExecutor());

        h.store.put(0, new FakeBitmap(0, 100), 0);
        h.store.attachDisk(folder.getRoot(), "doc");
        h.store.detachDisk();
        encoder.runAll();

        assertFalse(h.seeder.exists("doc", key(0, 0)));
    }

    @Test
    public void invalidatePage_dropsMemoryKeepsDiskAndDoesNotResurrectOldTag() {
        Harness h = new Harness(new SameThreadExecutor(), new SameThreadExecutor());
        h.store.attachDisk(folder.getRoot(), "doc");
        h.store.put(0, new FakeBitmap(1, 100), 0);
        assertTrue(h.seeder.exists("doc", key(0, 0)));

        h.store.invalidatePage(0);
        assertNull(h.store.peek(0));
        assertTrue(h.seeder.exists("doc", key(0, 0)));

        h.tags.set(0, 7);
        assertNull(h.store.peek(0));

        Flag callback = new Flag();
        h.store.requestDecode(0, callback);
        assertNull(h.store.peek(0));
        assertFalse(callback.ran);
        assertTrue(h.seeder.exists("doc", key(0, 0)));
    }

    @Test
    public void requestDecode_diskHitPublishesToMemoryAndRunsCallback() {
        Harness h = new Harness(new SameThreadExecutor(), new SameThreadExecutor());
        h.store.attachDisk(folder.getRoot(), "doc");
        h.store.put(0, new FakeBitmap(7, 100), 0);
        h.store.invalidatePage(0);

        Flag callback = new Flag();
        h.store.requestDecode(0, callback);

        assertTrue(callback.ran);
        FakeBitmap published = h.store.peek(0);
        assertNotNull(published);
        assertEquals(7, published.id);
    }

    @Test
    public void requestDecode_dedupsConcurrentRequestsIntoOneDecode() {
        ManualExecutor decoder = new ManualExecutor();
        Harness h = new Harness(new SameThreadExecutor(), decoder);
        h.store.attachDisk(folder.getRoot(), "doc");
        h.store.put(0, new FakeBitmap(7, 100), 0);
        h.store.invalidatePage(0);

        Flag first = new Flag();
        Flag second = new Flag();
        h.store.requestDecode(0, first);
        h.store.requestDecode(0, second);
        assertEquals(1, decoder.size());

        decoder.runAll();

        assertTrue(first.ran);
        assertTrue(second.ran);
        assertEquals(1, h.codec.decodeCalls);
        assertNotNull(h.store.peek(0));
    }

    @Test
    public void requestDecode_corruptFileDeletedAndMisses() {
        Harness h = new Harness(new SameThreadExecutor(), new SameThreadExecutor());
        h.store.attachDisk(folder.getRoot(), "doc");
        h.seeder.write("doc", key(0, 0), new byte[]{1, 2, 3});

        Flag callback = new Flag();
        h.store.requestDecode(0, callback);

        assertFalse(h.seeder.exists("doc", key(0, 0)));
        assertNull(h.store.peek(0));
        assertFalse(callback.ran);
    }

    @Test
    public void requestDecode_decodeAfterTagChangeIsDiscarded() {
        ManualExecutor decoder = new ManualExecutor();
        Harness h = new Harness(new SameThreadExecutor(), decoder);
        h.store.attachDisk(folder.getRoot(), "doc");
        h.store.put(0, new FakeBitmap(7, 100), 0);
        h.store.invalidatePage(0);

        Flag callback = new Flag();
        h.store.requestDecode(0, callback);
        h.tags.set(0, 5);
        int recycledBefore = h.adapter.recycled;
        decoder.runAll();

        assertNull(h.store.peek(0));
        assertFalse(callback.ran);
        assertTrue(h.adapter.recycled > recycledBefore);
    }

    @Test
    public void reKeyOnSave_doesNotResurrectPreAnnotationPreview() {
        Harness h = new Harness(new SameThreadExecutor(), new SameThreadExecutor());
        h.store.attachDisk(folder.getRoot(), "oldKey");
        h.store.put(0, new FakeBitmap(1, 100), 0);
        assertTrue(h.seeder.exists("oldKey", key(0, 0)));

        h.store.invalidatePage(0);
        h.tags.set(0, 99);
        h.gens.bump(0);
        h.store.put(0, new FakeBitmap(2, 100), 1);

        h.tags.set(0, 0);
        h.store.attachDisk(folder.getRoot(), "newKey");

        assertNull(h.store.peek(0));

        Flag callback = new Flag();
        h.store.requestDecode(0, callback);
        assertNull(h.store.peek(0));
        assertFalse(callback.ran);

        assertTrue(h.seeder.exists("oldKey", key(0, 0)));
        assertFalse(h.seeder.exists("newKey", key(0, 0)));
    }

    @Test
    public void close_queuedEncodeAfterCloseWritesNothing() {
        ManualExecutor encoder = new ManualExecutor();
        Harness h = new Harness(encoder, new SameThreadExecutor());
        h.store.attachDisk(folder.getRoot(), "doc");
        encoder.runAll();

        h.store.put(0, new FakeBitmap(1, 100), 0);
        h.store.close();
        encoder.runAll();

        assertFalse(h.seeder.exists("doc", key(0, 0)));
        assertNull(h.store.peek(0));
    }

    @Test
    public void dropMemory_clearsMemoryAndRecyclesThroughPool() {
        Harness h = new Harness(new SameThreadExecutor(), new SameThreadExecutor());
        FakeBitmap bitmap = new FakeBitmap(1, 100);
        h.store.put(0, bitmap, 0);

        h.store.dropMemory();

        assertNull(h.store.peek(0));
        assertTrue(bitmap.recycled);
    }

    @Test
    public void memoryEviction_routesEvictedBitmapToPool() {
        FakeAdapter adapter = new FakeAdapter();
        FakeCodec codec = new FakeCodec();
        PreviewStore<FakeBitmap> store = new PreviewStore<>(adapter, codec,
                new FakeGenerations(), new FakeTags(), new FakeTransient(),
                new SameThreadExecutor(), new SameThreadExecutor(), 150, BIG, POOL_CAP);
        store.setBucket(BUCKET);

        FakeBitmap first = new FakeBitmap(0, 100);
        store.put(0, first, 0);
        store.put(1, new FakeBitmap(1, 100), 0);

        assertSame(first, store.getPool().acquire(BUCKET));
    }

    @Test
    public void requestDecode_withoutDisk_runsMiss() {
        Harness h = new Harness(new SameThreadExecutor(), new SameThreadExecutor());
        Flag published = new Flag();
        Flag missed = new Flag();

        h.store.requestDecode(0, published, missed);

        assertFalse(published.ran);
        assertTrue(missed.ran);
    }

    @Test
    public void requestDecode_missingFile_runsMiss() {
        Harness h = new Harness(new SameThreadExecutor(), new SameThreadExecutor());
        h.store.attachDisk(folder.getRoot(), "doc");
        Flag published = new Flag();
        Flag missed = new Flag();

        h.store.requestDecode(0, published, missed);

        assertFalse(published.ran);
        assertTrue(missed.ran);
        assertNull(h.store.peek(0));
    }

    @Test
    public void encode_evictionDuringEncode_defersPoolReleaseUntilEncodeDone() {
        ManualExecutor encoder = new ManualExecutor();
        FakeAdapter adapter = new FakeAdapter();
        final FakeCodec inner = new FakeCodec();
        final PreviewStore<FakeBitmap>[] holder = new PreviewStore[1];
        PreviewCodec<FakeBitmap> codec = new PreviewCodec<FakeBitmap>() {
            @Override
            public byte[] encode(FakeBitmap bitmap) {
                holder[0].put(1, new FakeBitmap(1, 100), 0);
                assertNull(holder[0].getPool().acquire(BUCKET));
                return inner.encode(bitmap);
            }

            @Override
            public FakeBitmap decode(byte[] data) {
                return inner.decode(data);
            }
        };
        PreviewStore<FakeBitmap> store = new PreviewStore<>(adapter, codec,
                new FakeGenerations(), new FakeTags(), new FakeTransient(),
                encoder, new SameThreadExecutor(), 150, BIG, POOL_CAP);
        holder[0] = store;
        store.setBucket(BUCKET);
        store.attachDisk(folder.getRoot(), "doc");
        encoder.runAll();

        FakeBitmap first = new FakeBitmap(0, 100);
        store.put(0, first, 0);
        encoder.runAll();

        assertSame(first, store.getPool().acquire(BUCKET));
    }
}
