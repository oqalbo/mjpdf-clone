package com.shockwave.pdfium;

public class PageTextTooLargeException extends RuntimeException {

    private final int charCount;
    private final int limit;

    public PageTextTooLargeException(int charCount, int limit) {
        super("page text has " + charCount + " characters, limit is " + limit);
        this.charCount = charCount;
        this.limit = limit;
    }

    public int getCharCount() {
        return charCount;
    }

    public int getLimit() {
        return limit;
    }
}
