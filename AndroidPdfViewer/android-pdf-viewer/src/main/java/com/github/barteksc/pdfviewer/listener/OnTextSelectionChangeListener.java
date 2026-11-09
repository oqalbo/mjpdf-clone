package com.github.barteksc.pdfviewer.listener;

import android.graphics.RectF;

public interface OnTextSelectionChangeListener {
    void onTextSelectionChanged(RectF viewBounds, int pageIndex);

    void onTextSelectionCleared();
}
