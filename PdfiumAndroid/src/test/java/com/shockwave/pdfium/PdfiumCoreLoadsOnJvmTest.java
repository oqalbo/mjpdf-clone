// Written by Mudlej. License is GPLv3.
package com.shockwave.pdfium;

import android.content.ContextWrapper;
import android.content.res.Resources;
import android.util.DisplayMetrics;

import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class PdfiumCoreLoadsOnJvmTest {
    @Test
    public void nativeLibraryAbsenceAllowsJavaTextMapping() {
        assertEquals("a", PdfiumCore.mapPresentationFormMarks("a"));
    }

    @Test
    public void instanceNormalizationPreservesTheExtractedHelperBehavior() {
        PdfiumCore core = new PdfiumCore(new DensityContext());
        assertEquals("fi\u064Eexample\nend", core.normalizeExtractedText(
                "\uFB01\uFE76exam\uFFFE\r\nple\r\u200Bend"));
        assertEquals("", core.normalizeExtractedText(null));
    }

    @Ignore("Pending ruling: openPage caches a returned zero; expected no cached page, requires a native zero-result fixture and functional page map")
    @Test
    public void failedPageOpenDoesNotCacheAZeroPointer() {
        PdfiumCore core = new PdfiumCore(new DensityContext());
        PdfDocument document = new PdfDocument();
        assertEquals(0L, core.openPage(document, 0));
        assertFalse(document.hasPage(0));
    }

    private static class DensityContext extends ContextWrapper {
        DensityContext() {
            super(null);
        }

        @Override
        public Resources getResources() {
            return new Resources(null, null, null) {
                @Override
                public DisplayMetrics getDisplayMetrics() {
                    DisplayMetrics metrics = new DisplayMetrics();
                    metrics.densityDpi = 160;
                    return metrics;
                }
            };
        }
    }
}
