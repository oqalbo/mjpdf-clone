// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer;

import android.graphics.RectF;
import com.shockwave.pdfium.PdfDocument;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class HighlightHitTesting {
    private static final float HIGHLIGHT_MATCH_TOLERANCE = 1.5f;

    private HighlightHitTesting() {}

    static boolean groupMatchesRects(List<PdfDocument.HighlightAnnotation> members, List<RectF> rects) {
        if (members.size() != rects.size()) {
            return false;
        }
        boolean[] used = new boolean[members.size()];
        for (RectF rect : rects) {
            boolean matched = false;
            for (int i = 0; i < members.size(); i++) {
                if (used[i]) {
                    continue;
                }
                RectF bounds = members.get(i).getBounds();
                if (bounds != null && pdfRectsAlmostEqual(rect, bounds, HIGHLIGHT_MATCH_TOLERANCE)) {
                    used[i] = true;
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    static boolean pdfRectsAlmostEqual(RectF a, RectF b, float tolerance) {
        return Math.abs(Math.min(a.left, a.right) - Math.min(b.left, b.right)) <= tolerance
                && Math.abs(Math.max(a.left, a.right) - Math.max(b.left, b.right)) <= tolerance
                && Math.abs(Math.min(a.top, a.bottom) - Math.min(b.top, b.bottom)) <= tolerance
                && Math.abs(Math.max(a.top, a.bottom) - Math.max(b.top, b.bottom)) <= tolerance;
    }

    static boolean pdfRectContainsPoint(RectF rect, float x, float y, float tolerance) {
        float left = Math.min(rect.left, rect.right);
        float right = Math.max(rect.left, rect.right);
        float bottom = Math.min(rect.top, rect.bottom);
        float top = Math.max(rect.top, rect.bottom);
        return x >= left - tolerance && x <= right + tolerance
                && y >= bottom - tolerance && y <= top + tolerance;
    }

    static float[] unionGroupBounds(List<PdfDocument.HighlightAnnotation> annotations,
                                          String group, RectF fallback) {
        if (group == null || group.isEmpty()) {
            return fallback == null ? null : new float[]{fallback.left, fallback.top, fallback.right, fallback.bottom};
        }
        float left = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        float top = -Float.MAX_VALUE;
        float bottom = Float.MAX_VALUE;
        boolean found = false;
        for (PdfDocument.HighlightAnnotation candidate : annotations) {
            if (!group.equals(candidate.getGroupKey())) {
                continue;
            }
            RectF b = candidate.getBounds();
            if (b == null) {
                continue;
            }
            left = Math.min(left, Math.min(b.left, b.right));
            right = Math.max(right, Math.max(b.left, b.right));
            top = Math.max(top, Math.max(b.top, b.bottom));
            bottom = Math.min(bottom, Math.min(b.top, b.bottom));
            found = true;
        }
        if (!found) {
            return fallback == null ? null : new float[]{fallback.left, fallback.top, fallback.right, fallback.bottom};
        }
        return new float[]{left, top, right, bottom};
    }

    static boolean pointNearFormFieldRect(float[] rects, float pdfX, float pdfY, float tolerance) {
        for (int i = 0; i + 3 < rects.length; i += 4) {
            float left = Math.min(rects[i], rects[i + 2]) - tolerance;
            float right = Math.max(rects[i], rects[i + 2]) + tolerance;
            float bottom = Math.min(rects[i + 1], rects[i + 3]) - tolerance;
            float top = Math.max(rects[i + 1], rects[i + 3]) + tolerance;
            if (pdfX >= left && pdfX <= right && pdfY >= bottom && pdfY <= top) {
                return true;
            }
        }
        return false;
    }

    static RectF findHighlightPdfBounds(List<PdfDocument.HighlightAnnotation> annotations,
                                        String groupKey, int annotationIndex) {
        if (groupKey != null && !groupKey.isEmpty()) {
            for (PdfDocument.HighlightAnnotation candidate : annotations) {
                if (groupKey.equals(candidate.getGroupKey())) {
                    return candidate.getBounds();
                }
            }
        }
        for (PdfDocument.HighlightAnnotation candidate : annotations) {
            if (candidate.getAnnotationIndex() == annotationIndex) {
                return candidate.getBounds();
            }
        }
        return null;
    }

    static PdfDocument.HighlightAnnotation findHighlightAnnotationMatching(
            List<PdfDocument.HighlightAnnotation> annotations, List<RectF> rects) {
        Map<String, List<PdfDocument.HighlightAnnotation>> groups = new HashMap<>();
        for (PdfDocument.HighlightAnnotation candidate : annotations) {
            String group = candidate.getGroupKey();
            if (group == null || group.isEmpty()) {
                continue;
            }
            List<PdfDocument.HighlightAnnotation> members = groups.get(group);
            if (members == null) {
                members = new ArrayList<>();
                groups.put(group, members);
            }
            members.add(candidate);
        }

        for (Map.Entry<String, List<PdfDocument.HighlightAnnotation>> entry : groups.entrySet()) {
            if (!groupMatchesRects(entry.getValue(), rects)) {
                continue;
            }
            return entry.getValue().get(0);
        }
        return null;
    }
}
