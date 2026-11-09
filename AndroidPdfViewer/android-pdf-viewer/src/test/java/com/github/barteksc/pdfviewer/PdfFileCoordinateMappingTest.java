// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.DisplayMetrics;
import com.github.barteksc.pdfviewer.support.PdfiumTestSupport;
import com.github.barteksc.pdfviewer.support.Rects;
import com.shockwave.pdfium.util.Size;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PdfFileCoordinateMappingTest extends PdfiumTestSupport.InitializedTest {
    private static final float TOLERANCE = 0.001f;

    @Test
    public void rotatedMediaBoxMapsKnownPointsAndRoundTripsUserCoordinates() {
        float[][] expected = {{100, 592}, {200, 100}, {512, 200}, {592, 512}};
        float[][] points = {{110, 220}, {10, 20}, {622, 812}, {316.25f, 416.75f}};
        for (int rotation = 0; rotation < 4; rotation++) {
            PdfFile.PageGeometry geometry = new PdfFile.PageGeometry(10, 20, 622, 812, rotation);
            float[] frame = new float[2];
            PdfFile.userToFrameTopDown(geometry, 110, 220, frame);
            assertArrayEquals(expected[rotation], frame, TOLERANCE);
            for (float[] point : points) {
                PdfFile.userToFrameTopDown(geometry, point[0], point[1], frame);
                float[] user = new float[2];
                PdfFile.frameTopDownToUser(geometry, frame[0], frame[1], user);
                assertArrayEquals("rotation " + rotation, point, user, TOLERANCE);
            }
        }
    }

    @Test
    public void oddQuarterTurnsSwapFrameWidthAndHeight() {
        for (int rotation = 0; rotation < 4; rotation++) {
            PdfFile.PageGeometry geometry = new PdfFile.PageGeometry(10, 20, 622, 812, rotation);
            assertEquals(rotation % 2 == 0 ? 612 : 792, geometry.frameWidth(), 0);
            assertEquals(rotation % 2 == 0 ? 792 : 612, geometry.frameHeight(), 0);
        }
    }

    @Test
    public void capturedGeometryUsesMediaBoxOriginAndRotation() {
        PdfFileTestSupport builder = new PdfFileTestSupport(new Size(612, 792)).view(612, 792);
        builder.geometry(0, 10, 20, 622, 812, 1);
        PdfFile file = builder.build();
        file.ensurePageGeometry(0);
        assertEquals(792, file.getPageFrameSize(0).getWidth(), 0);
        assertEquals(612, file.getPageFrameSize(0).getHeight(), 0);
        assertArrayEquals(new float[]{10, 20}, file.documentToPdfValues(0, 1, 0, 0), TOLERANCE);
    }

    @Test
    public void malformedGeometryFallsBackToOriginalPointSize() {
        float[][] malformed = {
                {10, 20, 622, 812, 4},
                {622, 20, 10, 812, 0},
                {10, 812, 622, 20, 0},
                {10, 20, 622, 812}
        };
        for (float[] values : malformed) {
            PdfFileTestSupport builder = new PdfFileTestSupport(new Size(612, 792)).view(612, 792);
            builder.geometry(0, values);
            PdfFile file = builder.build();
            file.ensurePageGeometry(0);
            verify(builder.core).getPageGeometry(builder.document, 0);
            assertEquals(612, file.getPageFrameSize(0).getWidth(), 0);
            assertEquals(792, file.getPageFrameSize(0).getHeight(), 0);
            assertArrayEquals(new float[]{0, 792}, file.documentToPdfValues(0, 1, 0, 0), TOLERANCE);
        }
    }

    @Test
    public void frameRectangleSortsCornersAfterZoomAndDocumentTranslation() {
        for (boolean vertical : new boolean[]{true, false}) {
            PdfFile file = new PdfFileTestSupport(new Size(100, 200), new Size(50, 100))
                    .vertical(vertical).build();
            float[] expected = vertical ? new float[]{70, 460, 130, 580} : new float[]{240, 140, 300, 260};
            assertArrayEquals(expected,
                    file.frameRectToDocumentValues(1, 2, 40, 80, 10, 20, false), TOLERANCE);
        }
    }

    @Test
    public void signatureYUsesFrameWidthAndMatchesStampDrawingScale() throws Exception {
        PdfFileTestSupport builder = new PdfFileTestSupport(new Size(400, 800)).view(400, 800);
        PdfFile file = spy(builder.build());
        doReturn(false).when(file).openPage(0);
        RectF pdfRect = measurableRect(40, 700, 240, 650);
        RectF frameRect = measurableRect(40, 100, 240, 150);
        RectF documentRect = measurableRect(80, 200, 480, 300);
        float[][] strokes = {{0.25f, 0.2f, 0.5f, 0.1f, 0.75f, 0.2f, 1, 0.25f}};
        when(builder.core.addSignatureContent(eq(builder.document), eq(0), any(float[][].class),
                eq(0xff123456), eq(2f))).thenReturn(true);
        assertTrue(file.addSignature(0, pdfRect, strokes, 0xff123456, 0.01f));
        ArgumentCaptor<float[][]> mapped = ArgumentCaptor.forClass(float[][].class);
        verify(builder.core).addSignatureContent(eq(builder.document), eq(0), mapped.capture(),
                eq(0xff123456), eq(2f));
        assertArrayEquals(new float[]{90, 660, 140, 680, 190, 660, 240, 650},
                mapped.getValue()[0], TOLERANCE);

        PDFView view = mock(PDFView.class);
        Resources resources = mock(Resources.class);
        DisplayMetrics metrics = new DisplayMetrics();
        metrics.density = 1;
        when(resources.getDisplayMetrics()).thenReturn(metrics);
        when(view.getResources()).thenReturn(resources);
        when(view.getZoom()).thenReturn(2f);
        view.pdfFile = file;
        doReturn(frameRect).when(file).userRectToFrame(0, 40, 650, 240, 700);
        doReturn(documentRect).when(file).frameRectToDocument(0, 2, 40, 100, 240, 150, false);
        try (MockedConstruction<Paint> paints = mockConstruction(Paint.class);
             MockedConstruction<Path> paths = mockConstruction(Path.class);
             MockedConstruction<Matrix> matrices = mockConstruction(Matrix.class);
             MockedConstruction<RectF> rects = mockConstruction(RectF.class, (rect, context) ->
                     doAnswer(invocation -> {
                         RectF source = invocation.getArgument(0);
                         rect.left = source.left;
                         rect.top = source.top;
                         rect.right = source.right;
                         rect.bottom = source.bottom;
                         return null;
                     }).when(rect).set(any(RectF.class)))) {
            StampPlacementManager manager = new StampPlacementManager(view);
            manager.start(0, pdfRect, strokes, 0xff123456, 0.01f);
            manager.draw(mock(Canvas.class));
            ArgumentCaptor<Float> scaleX = ArgumentCaptor.forClass(Float.class);
            ArgumentCaptor<Float> scaleY = ArgumentCaptor.forClass(Float.class);
            verify(matrices.constructed().get(0)).setScale(scaleX.capture(), scaleY.capture());
            verify(matrices.constructed().get(0)).postTranslate(80, 200);
            assertEquals(400, scaleX.getValue(), 0);
            assertEquals(400, scaleY.getValue(), 0);
            float[] frame = new float[2];
            PdfFile.userToFrameTopDown(new PdfFile.PageGeometry(0, 0, 400, 800, 0),
                    mapped.getValue()[0][0], mapped.getValue()[0][1], frame);
            assertEquals(180, frame[0] * 2, TOLERANCE);
            assertEquals(280, frame[1] * 2, TOLERANCE);
            assertEquals(frame[0] * 2, 80 + strokes[0][0] * scaleX.getValue(), TOLERANCE);
            assertEquals(frame[1] * 2, 200 + strokes[0][1] * scaleY.getValue(), TOLERANCE);
        }
    }

    private RectF measurableRect(float left, float top, float right, float bottom) {
        RectF rect = spy(Rects.rectF(left, top, right, bottom));
        doReturn(right - left).when(rect).width();
        doReturn(bottom - top).when(rect).height();
        return rect;
    }
}
