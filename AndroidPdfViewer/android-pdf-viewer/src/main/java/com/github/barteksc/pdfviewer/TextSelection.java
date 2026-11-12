package com.github.barteksc.pdfviewer;

final class TextSelection {

    int anchorPage;
    int anchorChar;
    int focusPage;
    int focusChar;

    boolean isEmpty() {
        return anchorPage == focusPage && anchorChar == focusChar;
    }

    boolean isSinglePage() {
        return startPage() == endPage();
    }

    int startPage() {
        return isReversed() ? focusPage : anchorPage;
    }

    int startChar() {
        return isReversed() ? focusChar : anchorChar;
    }

    int endPage() {
        return isReversed() ? anchorPage : focusPage;
    }

    int endChar() {
        return isReversed() ? anchorChar : focusChar;
    }

    private boolean isReversed() {
        return focusPage < anchorPage
                || (focusPage == anchorPage && focusChar < anchorChar);
    }
}
