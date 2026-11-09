// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer.support;

import android.util.Log;
import com.shockwave.pdfium.PdfDocument;
import com.shockwave.pdfium.PdfiumCore;
import org.mockito.MockedStatic;
import org.junit.BeforeClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

public final class PdfiumTestSupport {
    private static boolean initialized;

    private PdfiumTestSupport() {}

    public static synchronized void ensureInitialized() {
        if (initialized) return;
        try (MockedStatic<Log> ignored = mockStatic(Log.class)) {
            mock(PdfiumCore.class);
            initialized = true;
        }
    }

    public static PdfiumCore mockedCore() {
        ensureInitialized();
        return mock(PdfiumCore.class);
    }

    public static PdfDocument mockedDocument() {
        ensureInitialized();
        return mock(PdfDocument.class);
    }

    public abstract static class InitializedTest {
        @BeforeClass
        public static void initializePdfium() {
            ensureInitialized();
        }
    }
}
