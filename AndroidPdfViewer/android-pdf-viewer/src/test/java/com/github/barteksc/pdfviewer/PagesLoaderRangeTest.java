// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer;

import android.graphics.Bitmap;
import com.github.barteksc.pdfviewer.support.PdfiumTestSupport;
import com.github.barteksc.pdfviewer.util.Constants;
import com.github.barteksc.pdfviewer.util.Util;
import com.shockwave.pdfium.util.Size;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class PagesLoaderRangeTest extends PdfiumTestSupport.InitializedTest {
    private static class Loader {
        final PDFView view = mock(PDFView.class);
        final List<RenderTask> tasks = new ArrayList<>();
        final List<String> events = new ArrayList<>();
        final PagesLoader loader;

        Loader(PdfFile file, int width, int height, boolean vertical) {
            view.pdfFile = file;
            view.cacheManager = mock(CacheManager.class);
            view.renderScheduler = mock(RenderScheduler.class);
            when(view.getWidth()).thenReturn(width);
            when(view.getHeight()).thenReturn(height);
            when(view.getZoom()).thenReturn(1f);
            when(view.isSwipeVertical()).thenReturn(vertical);
            when(view.peekPreview(anyInt())).thenAnswer(invocation -> {
                events.add("peek" + invocation.getArgument(0));
                return null;
            });
            doAnswer(invocation -> {
                events.add("preview" + invocation.getArgument(0));
                return null;
            }).when(view).requestPreview(anyInt());
            doAnswer(invocation -> {
                RenderTask task = invocation.getArgument(0);
                tasks.add(task);
                events.add("tile" + task.page);
                return null;
            }).when(view.renderScheduler).submit(any());
            try (MockedStatic<Util> ignored = mockStatic(Util.class)) {
                loader = new PagesLoader(view);
            }
        }
    }

    @Test
    public void verticalRangeIncludesAnExtraRowButLoadsOnlyPositiveHeightTiles() {
        PdfFile file = PdfFileTestSupport.uniform(1, 256, 1024)
                .view(256, 1024).vertical(true).rtl(false).pagesPerRow(1).spacing(0).build();
        List<PagesLoader.RenderRange> ranges = PagesLoader.getRenderRangeList(file, 1, true, 0, 0, -256, -1024);
        assertEquals(1, ranges.size());
        assertEquals(4, ranges.get(0).gridSize.rows);
        assertEquals(4, ranges.get(0).rightBottom.row);
        Loader loader = new Loader(file, 256, 1024, true);
        loader.loader.loadPages();
        assertEquals(4, loader.tasks.size());
        assertEquals(0.75f, loader.tasks.get(3).boundsTop, 0f);
        assertEquals(1f, loader.tasks.get(3).boundsBottom, 0f);
        assertEquals(256f, loader.tasks.get(3).renderHeight, 0f);
    }

    @Test
    public void horizontalRangeFloorsAndLoadsTheLastVisibleColumn() {
        PdfFile file = PdfFileTestSupport.uniform(1, 1024, 256)
                .view(1024, 256).vertical(false).rtl(false).pagesPerRow(1).spacing(0).build();
        List<PagesLoader.RenderRange> ranges = PagesLoader.getRenderRangeList(file, 1, false, 0, 0, -600, -256);
        assertEquals(1, ranges.size());
        assertEquals(4, ranges.get(0).gridSize.cols);
        assertEquals(2, ranges.get(0).rightBottom.col);
        Loader loader = new Loader(file, 600, 256, false);
        loader.loader.loadPages();
        assertEquals(3, loader.tasks.size());
        assertEquals(0.5f, loader.tasks.get(2).boundsLeft, 0f);
        assertEquals(0.75f, loader.tasks.get(2).boundsRight, 0f);
    }

    @Test(timeout = 1000)
    public void zeroSizePageProducesNoTilesAndTerminates() {
        PdfFile file = new PdfFileTestSupport(new Size(0, 0)).view(256, 256).spacing(0).build();
        PagesLoader.GridSize grid = new PagesLoader.GridSize();
        PagesLoader.getPageColsRows(file, 1, grid, 0);
        assertEquals(0, grid.rows);
        assertEquals(0, grid.cols);
        Loader loader = new Loader(file, 256, 256, true);
        loader.loader.loadPages();
        assertEquals(0, loader.tasks.size());
        assertEquals(Arrays.asList("peek0", "preview0"), loader.events);
    }

    @Test
    @Ignore("Pending ruling: a viewport over only the left page loads two right page tiles instead of none")
    public void leftColumnViewportDoesNotLoadRightPageTiles() {
        PdfFile file = PdfFileTestSupport.uniform(2, 512, 512)
                .view(1024, 512).vertical(true).rtl(false).pagesPerRow(2).spacing(0).build();
        Loader loader = new Loader(file, 256, 512, true);
        loader.loader.loadPages();
        assertEquals(0L, loader.tasks.stream().filter(task -> task.page == 1).count());
        assertEquals(4L, loader.tasks.stream().filter(task -> task.page == 0).count());
    }

    @Test
    public void tileCapIsSharedAcrossPages() {
        int original = Constants.Cache.CACHE_SIZE;
        try {
            Constants.Cache.CACHE_SIZE = 5;
            PdfFile file = PdfFileTestSupport.uniform(3, 512, 512)
                    .view(512, 512).vertical(true).rtl(false).pagesPerRow(1).spacing(0).build();
            Loader loader = new Loader(file, 512, 1536, true);
            loader.loader.loadPages();
            assertEquals(5, loader.tasks.size());
            assertEquals(4L, loader.tasks.stream().filter(task -> task.page == 0).count());
            assertEquals(1L, loader.tasks.stream().filter(task -> task.page == 1).count());
            assertEquals(0L, loader.tasks.stream().filter(task -> task.page == 2).count());
            assertEquals(4, loader.tasks.get(4).cacheOrder);
            verify(loader.view).requestPreview(2);
        } finally {
            Constants.Cache.CACHE_SIZE = original;
        }
    }

    @Test
    public void roundedEdgeCellsTrimBothDimensionsToExactlyOne() {
        PdfFile file = PdfFileTestSupport.uniform(1, 1792, 1792)
                .view(1792, 1792).vertical(true).rtl(false).pagesPerRow(1).spacing(0).build();
        Loader loader = new Loader(file, 1792, 1792, true);
        loader.loader.loadPages();
        assertEquals(49, loader.tasks.size());
        RenderTask last = loader.tasks.get(48);
        assertEquals(6 * (1f / 7), last.boundsLeft, 0f);
        assertEquals(6 * (1f / 7), last.boundsTop, 0f);
        assertEquals(1f, last.boundsRight, 0f);
        assertEquals(1f, last.boundsBottom, 0f);
        assertEquals((256 / (1f / 7)) * (1 - 6 * (1f / 7)), last.renderWidth, 0f);
        assertEquals(last.renderWidth, last.renderHeight, 0f);
    }

    @Test
    public void missingPreviewsForEveryPageAreRequestedBeforeAnyTiles() {
        PdfFile file = PdfFileTestSupport.uniform(3, 256, 256)
                .view(256, 256).vertical(true).rtl(false).pagesPerRow(1).spacing(0).build();
        Loader loader = new Loader(file, 256, 768, true);
        Bitmap preview = mock(Bitmap.class);
        doAnswer(invocation -> {
            loader.events.add("peek1");
            return preview;
        }).when(loader.view).peekPreview(1);
        loader.loader.loadPages();
        assertEquals(Arrays.asList("peek0", "preview0", "peek1", "peek2", "preview2", "tile0", "tile1", "tile2"), loader.events);
        verify(loader.view, never()).requestPreview(1);
    }
}
