package com.github.barteksc.pdfviewer;

final class TextSelection {

    int pageIndex;
    int baseChar;
    int extentChar;

    boolean isEmpty() {
        return baseChar == extentChar;
    }

    int startChar() {
        return Math.min(baseChar, extentChar);
    }

    int endChar() {
        return Math.max(baseChar, extentChar);
    }

    int count() {
        return endChar() - startChar();
    }
}
