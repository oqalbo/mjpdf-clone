// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer;

import com.github.barteksc.pdfviewer.util.Constants;

final class PreviewBucketPolicy {

    private PreviewBucketPolicy() {
    }

    static int bucketFor(float fittedMaxPageWidth, boolean lowTier) {
        int ceiling = Math.round(fittedMaxPageWidth);
        float ratio = lowTier ? Constants.PREVIEW_BUCKET_RATIO_LOW : Constants.PREVIEW_BUCKET_RATIO_NORMAL;
        int bucket = Math.round(ratio * fittedMaxPageWidth);
        if (bucket < Constants.PREVIEW_BUCKET_MIN_PX) {
            bucket = Constants.PREVIEW_BUCKET_MIN_PX;
        }
        if (bucket > Constants.PREVIEW_BUCKET_MAX_PX) {
            bucket = Constants.PREVIEW_BUCKET_MAX_PX;
        }
        if (bucket > ceiling) {
            bucket = ceiling;
        }
        if (bucket < 1) {
            bucket = 1;
        }
        return bucket;
    }
}
