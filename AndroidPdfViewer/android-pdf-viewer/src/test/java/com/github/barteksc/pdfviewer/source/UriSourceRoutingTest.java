// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer.source;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import org.junit.Test;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;

public class UriSourceRoutingTest {

    @Test
    public void fileSchemeWithAPathUsesFileSource() throws IOException {
        Context context = mock(Context.class);
        Uri uri = uri("file", "/documents/a book.pdf");
        File file = new File("/documents/a book.pdf");
        ParcelFileDescriptor descriptor = mock(ParcelFileDescriptor.class);
        try (MockedStatic<FileSource> files = mockStatic(FileSource.class)) {
            files.when(() -> FileSource.openReadDescriptor(context, file)).thenReturn(descriptor);

            assertSame(descriptor, UriSource.openReadDescriptor(context, uri));

            files.verify(() -> FileSource.openReadDescriptor(context, file));
            verifyNoInteractions(context);
        }
    }

    @Test
    public void fileSchemeWithoutAPathUsesTheContentResolver() throws IOException {
        assertUsesContentResolver("file", null);
    }

    @Test
    public void otherSchemesUseTheContentResolverEvenWhenAPathExists() throws IOException {
        for (String scheme : new String[]{"content", "https", "custom", "FILE", null}) {
            assertUsesContentResolver(scheme, "/documents/a book.pdf");
        }
    }

    private static void assertUsesContentResolver(String scheme, String path) throws IOException {
        Context context = mock(Context.class);
        ContentResolver resolver = mock(ContentResolver.class);
        Uri uri = uri(scheme, path);
        ParcelFileDescriptor descriptor = mock(ParcelFileDescriptor.class);
        when(context.getContentResolver()).thenReturn(resolver);
        when(resolver.openFileDescriptor(uri, "r")).thenReturn(descriptor);
        try (MockedStatic<FileSource> files = mockStatic(FileSource.class)) {
            assertSame(descriptor, UriSource.openReadDescriptor(context, uri));

            verify(resolver).openFileDescriptor(uri, "r");
            files.verifyNoInteractions();
        }
    }

    private static Uri uri(String scheme, String path) {
        Uri uri = mock(Uri.class);
        when(uri.getScheme()).thenReturn(scheme);
        when(uri.getPath()).thenReturn(path);
        return uri;
    }
}
