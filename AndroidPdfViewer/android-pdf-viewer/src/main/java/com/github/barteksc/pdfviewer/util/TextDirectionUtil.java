package com.github.barteksc.pdfviewer.util;

public final class TextDirectionUtil {

    private TextDirectionUtil() {
    }

    public static boolean isRtl(int codePoint) {
        return (codePoint >= 0x0590 && codePoint <= 0x08FF)
                || (codePoint >= 0xFB1D && codePoint <= 0xFDFF)
                || (codePoint >= 0xFE70 && codePoint <= 0xFEFF);
    }
}
