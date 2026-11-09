// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer;

import android.graphics.Bitmap;
import android.graphics.Rect;
import com.github.barteksc.pdfviewer.model.CropBounds;
import com.github.barteksc.pdfviewer.model.CropMargins;
import com.github.barteksc.pdfviewer.support.PdfiumTestSupport;
import com.github.barteksc.pdfviewer.util.FitPolicy;
import com.shockwave.pdfium.PdfDocument;
import com.shockwave.pdfium.PdfiumCore;
import com.shockwave.pdfium.util.Size;
import com.shockwave.pdfium.util.SizeF;
import java.util.Arrays;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Before;
import org.mockito.MockedConstruction;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

final class PdfFileTestSupport {
    final PdfiumCore core = PdfiumTestSupport.mockedCore();
    final PdfDocument document = PdfiumTestSupport.mockedDocument();
    final Bitmap bitmap = mock(Bitmap.class);
    PdfFile file;
    private final List<Size> pageSizes;
    private Size viewSize = new Size(100, 200);
    private boolean vertical = true;
    private int spacing = 10;
    private boolean autoSpacing;
    private int pagesPerRow = 1;
    private boolean firstPageAlone;
    private boolean rightToLeft;
    private int[] userPages;
    private CropBounds crop;

    PdfFileTestSupport(Size... pageSizes) {
        this.pageSizes = Arrays.asList(pageSizes);
    }

    static PdfFileTestSupport uniform(int pageCount, int pageWidth, int pageHeight) {
        Size[] pageSizes = new Size[pageCount];
        Arrays.fill(pageSizes, new Size(pageWidth, pageHeight));
        return new PdfFileTestSupport(pageSizes);
    }

    PdfFileTestSupport geometry(int page, float... values) {
        when(core.getPageGeometry(document, page)).thenReturn(values);
        return this;
    }

    PdfFileTestSupport textPages() {
        Set<Integer> textPages = new HashSet<>();
        for (int page = 0; page < pageSizes.size(); page++) {
            Size pageSize = pageSizes.get(page);
            geometry(page, 0, 0, pageSize.getWidth(), pageSize.getHeight(), 0);
        }
        when(core.openPage(eq(document), anyInt())).thenReturn(1L);
        when(core.openTextPage(eq(document), anyInt())).thenAnswer(call -> {
            textPages.add(call.getArgument(1));
            return 1L;
        });
        when(core.hasTextPage(eq(document), anyInt())).thenAnswer(call -> textPages.contains(call.getArgument(1)));
        doAnswer(call -> { textPages.remove(call.getArgument(1)); return null; })
                .when(core).closeTextPage(eq(document), anyInt());
        doAnswer(call -> { textPages.clear(); return null; }).when(core).closeTextPages(document);
        return this;
    }

    PdfFileTestSupport chunkedRender() {
        when(bitmap.getWidth()).thenReturn(100);
        when(bitmap.getHeight()).thenReturn(200);
        when(core.renderPageBitmapChunkedStart(eq(document), eq(bitmap), anyInt(),
                anyInt(), anyInt(), anyInt(), anyInt(), anyBoolean(), anyInt())).thenReturn(41L);
        when(core.renderPageBitmapChunkedStatus(41L)).thenReturn(PdfiumCore.RENDER_STATUS_TO_BE_CONTINUED);
        when(core.renderPageBitmapChunkedContinue(document, 41L, 0)).thenReturn(PdfiumCore.RENDER_STATUS_DONE);
        return this;
    }

    boolean render(AtomicBoolean cancel) {
        try (MockedConstruction<Rect> ignored = mockConstruction(Rect.class, (rect, context) -> {
            when(rect.width()).thenReturn(100);
            when(rect.height()).thenReturn(200);
        })) {
            return file.renderFullPageBitmapCancellable(bitmap, 0, true, 0, cancel);
        }
    }

    PdfFileTestSupport view(int width, int height) {
        viewSize = new Size(width, height);
        return this;
    }

    PdfFileTestSupport vertical(boolean value) {
        vertical = value;
        return this;
    }

    PdfFileTestSupport spacing(int value) {
        spacing = value;
        return this;
    }

    PdfFileTestSupport autoSpacing(boolean value) {
        autoSpacing = value;
        return this;
    }

    PdfFileTestSupport pagesPerRow(int value) {
        pagesPerRow = value;
        return this;
    }

    PdfFileTestSupport firstPageAlone(boolean value) {
        firstPageAlone = value;
        return this;
    }

    PdfFileTestSupport rtl(boolean value) {
        rightToLeft = value;
        return this;
    }

    PdfFileTestSupport userPages(int... value) {
        userPages = value;
        return this;
    }

    PdfFileTestSupport crop(CropBounds value) {
        crop = value;
        return this;
    }

    PdfFile build() {
        when(core.getPageCount(document)).thenReturn(pageSizes.size());
        for (int page = 0; page < pageSizes.size(); page++) {
            Size size = pageSizes.get(page);
            when(core.getPageSize(document, page)).thenReturn(size);
            when(core.getPageSizePoint(document, page))
                    .thenReturn(new SizeF(size.getWidth(), size.getHeight()));
        }
        file = new PdfFile(core, document, FitPolicy.WIDTH, viewSize, userPages,
                vertical, spacing, autoSpacing, false, crop != null,
                crop == null ? null : CropMargins.single(crop), rightToLeft, pagesPerRow, firstPageAlone);
        return file;
    }

    public abstract static class PageLifetimeTest extends PdfiumTestSupport.InitializedTest {
        PdfFileTestSupport fixture;

        @Before
        public void setUp() {
            fixture = uniform(20, 100, 200).spacing(0).textPages();
            fixture.build();
        }
    }
}
