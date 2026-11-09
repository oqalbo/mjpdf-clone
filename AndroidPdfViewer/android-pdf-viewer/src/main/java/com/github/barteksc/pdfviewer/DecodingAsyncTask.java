/**
 * Copyright 2016 Bartosz Schiller
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.barteksc.pdfviewer;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.github.barteksc.pdfviewer.model.CropMargins;
import com.github.barteksc.pdfviewer.source.DocumentSource;
import com.github.barteksc.pdfviewer.util.FitPolicy;
import com.shockwave.pdfium.PdfDocument;
import com.shockwave.pdfium.PdfiumCore;
import com.shockwave.pdfium.util.Size;

import java.lang.ref.WeakReference;

class DecodingAsyncTask extends AsyncTask<Void, Void, Throwable> {

    private boolean cancelled;

    private WeakReference<PDFView> pdfViewReference;

    private PdfiumCore pdfiumCore;
    private String password;
    private DocumentSource docSource;
    private int[] userPages;
    private PdfFile pdfFile;

    private final Context context;
    private final Size viewSize;
    private final FitPolicy pageFitPolicy;
    private final boolean swipeVertical;
    private final int spacingPx;
    private final boolean autoSpacingEnabled;
    private final boolean fitEachPage;
    private final boolean cropMarginsEnabled;
    private final CropMargins cachedCropMargins;
    private final boolean horizontalReadingDirectionRtl;
    private final int pagesPerRow;
    private final boolean firstPageAlone;

    DecodingAsyncTask(DocumentSource docSource, String password, int[] userPages, PDFView pdfView, PdfiumCore pdfiumCore) {
        this.docSource = docSource;
        this.userPages = userPages;
        this.cancelled = false;
        this.pdfViewReference = new WeakReference<>(pdfView);
        this.password = password;
        this.pdfiumCore = pdfiumCore;
        this.context = pdfView.getContext();
        this.viewSize = new Size(pdfView.getWidth(), pdfView.getHeight());
        this.pageFitPolicy = pdfView.getPageFitPolicy();
        this.swipeVertical = pdfView.isSwipeVertical();
        this.spacingPx = pdfView.getSpacingPx();
        this.autoSpacingEnabled = pdfView.isAutoSpacingEnabled();
        this.fitEachPage = pdfView.isFitEachPage();
        this.cropMarginsEnabled = pdfView.isCropMarginsEnabled();
        this.cachedCropMargins = pdfView.getCachedCropMargins();
        this.horizontalReadingDirectionRtl = pdfView.isHorizontalReadingDirectionRtl();
        this.pagesPerRow = pdfView.getPagesPerRow();
        this.firstPageAlone = pdfView.isFirstPageAlone();
    }

    @Override
    protected Throwable doInBackground(Void... params) {
        PdfDocument pdfDocument = null;
        try {
            if (pdfViewReference.get() != null) {
                pdfDocument = docSource.createDocument(context, pdfiumCore, password);
                pdfFile = new PdfFile(pdfiumCore, pdfDocument, pageFitPolicy, viewSize,
                        userPages, swipeVertical, spacingPx, autoSpacingEnabled,
                        fitEachPage, cropMarginsEnabled, cachedCropMargins,
                        horizontalReadingDirectionRtl, pagesPerRow, firstPageAlone);
                return null;
            } else {
                return new NullPointerException("pdfView == null");
            }

        } catch (Throwable t) {
            if (pdfFile == null && pdfDocument != null) {
                try {
                    pdfiumCore.closeDocument(pdfDocument);
                } catch (Throwable closeError) {
                    Log.e(DecodingAsyncTask.class.getSimpleName(), "doInBackground: failed to close document", closeError);
                }
            }
            Log.e(DecodingAsyncTask.class.getSimpleName(), "doInBackground: ", t);
            return t;
        }
    }

    @Override
    protected void onPostExecute(Throwable t) {
        PDFView pdfView = pdfViewReference.get();
        if (pdfView != null) {
            if (t != null) {
                pdfView.loadError(t);
                return;
            }
            if (!cancelled) {
                pdfView.loadComplete(pdfFile);
            }
        }
    }

    @Override
    protected void onCancelled() {
        cancelled = true;
        if (pdfFile != null) {
            pdfFile.dispose();
            pdfFile = null;
        }
    }

    @Override
    protected void onCancelled(Throwable t) {
        onCancelled();
    }
}
