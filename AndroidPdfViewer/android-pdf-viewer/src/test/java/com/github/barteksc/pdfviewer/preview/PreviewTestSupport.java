// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer.preview;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

final class PreviewTestSupport {

    private PreviewTestSupport() {
    }

    static final class FakeBitmap {
        final int id;
        final int bytes;
        boolean recycled;

        FakeBitmap(int id, int bytes) {
            this.id = id;
            this.bytes = bytes;
        }
    }

    static final class FakeAdapter implements PreviewBitmapAdapter<FakeBitmap> {
        int recycled;

        @Override
        public int byteCount(FakeBitmap bitmap) {
            return bitmap.bytes;
        }

        @Override
        public void recycle(FakeBitmap bitmap) {
            bitmap.recycled = true;
            recycled++;
        }
    }

    static final class FakeCodec implements PreviewCodec<FakeBitmap> {
        int encodeCalls;
        int decodeCalls;

        @Override
        public byte[] encode(FakeBitmap bitmap) {
            encodeCalls++;
            byte[] data = new byte[8];
            writeInt(data, 0, bitmap.id);
            writeInt(data, 4, bitmap.bytes);
            return data;
        }

        @Override
        public FakeBitmap decode(byte[] data) {
            decodeCalls++;
            if (data == null || data.length != 8) {
                return null;
            }
            int id = readInt(data, 0);
            int bytes = readInt(data, 4);
            return new FakeBitmap(id, bytes);
        }

        private static void writeInt(byte[] data, int offset, int value) {
            data[offset] = (byte) (value >>> 24);
            data[offset + 1] = (byte) (value >>> 16);
            data[offset + 2] = (byte) (value >>> 8);
            data[offset + 3] = (byte) value;
        }

        private static int readInt(byte[] data, int offset) {
            return ((data[offset] & 0xFF) << 24)
                    | ((data[offset + 1] & 0xFF) << 16)
                    | ((data[offset + 2] & 0xFF) << 8)
                    | (data[offset + 3] & 0xFF);
        }
    }

    static final class FakeGenerations implements GenerationSource {
        private final Map<Integer, Integer> generations = new HashMap<>();

        @Override
        public int generationOf(int page) {
            Integer value = generations.get(page);
            return value == null ? 0 : value;
        }

        void bump(int page) {
            generations.put(page, generationOf(page) + 1);
        }
    }

    static final class FakeTags implements TagSource {
        private final Map<Integer, Integer> tags = new HashMap<>();

        @Override
        public int tagOf(int page) {
            Integer value = tags.get(page);
            return value == null ? 0 : value;
        }

        void set(int page, int tag) {
            tags.put(page, tag);
        }
    }

    static final class FakeTransient implements TransientPageFilter {
        private final Map<Integer, Boolean> flags = new HashMap<>();

        @Override
        public boolean isTransient(int page) {
            Boolean value = flags.get(page);
            return value != null && value;
        }

        void set(int page, boolean value) {
            flags.put(page, value);
        }
    }

    static final class SameThreadExecutor implements Executor {
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }

    static final class ManualExecutor implements Executor {
        private final List<Runnable> pending = new ArrayList<>();

        @Override
        public void execute(Runnable command) {
            pending.add(command);
        }

        int size() {
            return pending.size();
        }

        void runAll() {
            List<Runnable> snapshot = new ArrayList<>(pending);
            pending.clear();
            for (Runnable command : snapshot) {
                command.run();
            }
        }
    }

    static final class Flag implements Runnable {
        boolean ran;

        @Override
        public void run() {
            ran = true;
        }
    }
}
