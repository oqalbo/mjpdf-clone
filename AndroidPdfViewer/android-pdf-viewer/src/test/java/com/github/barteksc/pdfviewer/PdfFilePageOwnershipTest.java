// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer;

import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class PdfFilePageOwnershipTest extends PdfFileTestSupport.PageLifetimeTest {
    @Test
    public void renderingTakesOwnershipOfAPageOpenedForTextSelection() throws Exception {
        fixture.file.ensureTextPage(2);
        assertFalse(fixture.file.openPage(2));
        fixture.file.closeTextPage(2);
        verify(fixture.core).closeTextPage(fixture.document, 2);
        verify(fixture.core, never()).closePage(fixture.document, 2);
        assertFalse(fixture.file.pageHasError(2));
        fixture.file.closeRenderOwnedPage(2);
        verify(fixture.core).closePage(fixture.document, 2);
        verify(fixture.core, times(1)).openPage(fixture.document, 2);
    }

    @Test
    public void closingTextSelectionReleasesItsNativePage() throws Exception {
        fixture.file.ensureTextPage(2);
        fixture.file.closeTextPage(2);
        verify(fixture.core).closeTextPage(fixture.document, 2);
        verify(fixture.core).closePage(fixture.document, 2);
        assertTrue(fixture.file.openPage(2));
        verify(fixture.core, times(2)).openPage(fixture.document, 2);
    }

    @Test
    public void closingAllTextPagesClosesOnlyNativePagesStillOwnedByTextSelection() throws Exception {
        fixture.file.ensureTextPage(1);
        fixture.file.ensureTextPage(3);
        fixture.file.ensureTextPage(5);
        fixture.file.openPage(3);
        fixture.file.openPage(7);
        fixture.file.ensureTextPage(7);
        fixture.file.closeAllTextPages();
        verify(fixture.core).closeTextPages(fixture.document);
        verify(fixture.core).closePage(fixture.document, 1);
        verify(fixture.core).closePage(fixture.document, 5);
        verify(fixture.core, times(2)).closePage(eq(fixture.document), anyInt());
        assertFalse(fixture.file.pageHasError(3));
        assertFalse(fixture.file.pageHasError(7));
        fixture.file.closeAllTextPages();
        verify(fixture.core, times(2)).closePage(eq(fixture.document), anyInt());
    }

    @Test
    public void renderCloseRefusesPinnedTextOwnedAndOpenTextPages() throws Exception {
        fixture.file.openPage(0);
        fixture.file.pinPage(0);
        fixture.file.ensureTextPage(1);
        fixture.file.openPage(2);
        fixture.file.ensureTextPage(2);
        fixture.file.closeRenderOwnedPage(0);
        fixture.file.closeRenderOwnedPage(1);
        fixture.file.closeRenderOwnedPage(2);
        verify(fixture.core, never()).closePage(eq(fixture.document), anyInt());
        fixture.file.unpinPage();
        fixture.file.closeRenderOwnedPage(0);
        fixture.file.closeTextPage(2);
        fixture.file.closeRenderOwnedPage(2);
        verify(fixture.core).closePage(fixture.document, 0);
        verify(fixture.core).closePage(fixture.document, 2);
        verify(fixture.core, never()).closePage(fixture.document, 1);
    }
}
