// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer;

import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.github.barteksc.pdfviewer.exception.PageRenderingException;
import org.mockito.InOrder;

public class PdfFilePageLruTest extends PdfFileTestSupport.PageLifetimeTest {
    @Test
    public void thirteenthPageClosesTheLeastRecentlyUsedPage() throws Exception {
        for (int page = 0; page < 13; page++) {
            assertTrue(fixture.file.openPage(page));
        }
        verify(fixture.core).closePage(fixture.document, 0);
        verify(fixture.core, times(1)).closePage(eq(fixture.document), anyInt());
    }

    @Test
    public void reopeningAnOpenPageMovesItToTheMostRecentPosition() throws Exception {
        for (int page = 0; page < 12; page++) {
            fixture.file.openPage(page);
        }
        assertFalse(fixture.file.openPage(0));
        fixture.file.openPage(12);
        fixture.file.openPage(13);
        InOrder closes = inOrder(fixture.core);
        closes.verify(fixture.core).closePage(fixture.document, 1);
        closes.verify(fixture.core).closePage(fixture.document, 2);
        verify(fixture.core, never()).closePage(fixture.document, 0);
        verify(fixture.core, times(1)).openPage(fixture.document, 0);
    }

    @Test
    public void evictionSkipsPinnedPagesAndPagesWithOpenText() throws Exception {
        fixture.file.pinPage(0);
        for (int page = 0; page < 12; page++) {
            fixture.file.openPage(page);
        }
        fixture.file.ensureTextPage(1);
        fixture.file.openPage(12);
        verify(fixture.core).closePage(fixture.document, 2);
        verify(fixture.core, times(1)).closePage(eq(fixture.document), anyInt());
        fixture.file.unpinPage();
        fixture.file.openPage(13);
        verify(fixture.core).closePage(fixture.document, 0);
        verify(fixture.core, never()).closePage(fixture.document, 1);
    }

    @Test
    public void failedPageRemainsFailedWithoutRetryingNativeOpen() throws Exception {
        IllegalStateException failure = new IllegalStateException("native open failed");
        when(fixture.core.openPage(fixture.document, 3)).thenThrow(failure);
        PageRenderingException thrown = assertThrows(PageRenderingException.class, () -> fixture.file.openPage(3));
        assertSame(failure, thrown.getCause());
        assertTrue(fixture.file.pageHasError(3));
        assertFalse(fixture.file.openPage(3));
        fixture.file.ensureTextPage(3);
        assertTrue(fixture.file.pageHasError(3));
        verify(fixture.core, times(1)).openPage(fixture.document, 3);
        verify(fixture.core, never()).openTextPage(fixture.document, 3);
    }
}
