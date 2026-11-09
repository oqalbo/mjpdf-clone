// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer;

import java.util.HashMap;
import java.util.Map;

final class RenderTestSupport {

    private RenderTestSupport() {
    }

    static final class FakeGenerations implements RenderQueue.GenerationSource {

        private final Map<Integer, Integer> generations = new HashMap<>();

        @Override
        public int generationOf(int page) {
            Integer value = generations.get(page);
            return value == null ? 0 : value;
        }

        void set(int page, int generation) {
            generations.put(page, generation);
        }

        void bump(int page) {
            generations.put(page, generationOf(page) + 1);
        }
    }

    static RenderTask tile(int page, int priorityClass) {
        return new RenderTask(RenderTask.Kind.TILE, page, true, 0f, 0f, 1f, 1f,
                10f, 10f, false, 0, false, false, priorityClass);
    }

    static RenderTask preview(int page, int priorityClass) {
        return RenderTask.preview(page, 10f, 10f, false, priorityClass);
    }
}
