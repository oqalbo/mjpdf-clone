package com.shockwave.pdfium;

import android.graphics.RectF;
import android.os.ParcelFileDescriptor;
import android.util.ArrayMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PdfDocument {

    public static class Meta {
        String title;
        String author;
        String subject;
        String keywords;
        String creator;
        String producer;
        String creationDate;
        String modDate;
        int totalPages;

        public String getTitle() {
            return title;
        }

        public String getAuthor() {
            return author;
        }

        public String getSubject() {
            return subject;
        }

        public String getKeywords() {
            return keywords;
        }

        public String getCreator() {
            return creator;
        }

        public String getProducer() {
            return producer;
        }

        public String getCreationDate() {
            return creationDate;
        }

        public String getModDate() {
            return modDate;
        }

        public int getTotalPages() {
            return totalPages;
        }
    }

    public static class Bookmark {
        private List<Bookmark> children = new ArrayList<>();
        public String title;
        public long pageIdx;
        public long mNativePtr;

        public List<Bookmark> getChildren() {
            return children;
        }

        public void setChildren(List<Bookmark> newChildren) {
            children.addAll(newChildren);
        }

        public boolean hasChildren() {
            return !children.isEmpty();
        }

        public String getTitle() {
            return title;
        }

        public long getPageIdx() {
            return pageIdx;
        }
    }

    public static class Link {
        private RectF bounds;
        private Integer destPageIdx;
        private String uri;

        public Link(RectF bounds, Integer destPageIdx, String uri) {
            this.bounds = bounds;
            this.destPageIdx = destPageIdx;
            this.uri = uri;
        }

        public Integer getDestPageIdx() {
            return destPageIdx;
        }

        public String getUri() {
            return uri;
        }

        public RectF getBounds() {
            return bounds;
        }
    }

    public static class HighlightAnnotation {
        private int annotationIndex;
        private String groupKey;
        private RectF bounds;
        private String quote;
        private int color;
        private boolean appOwned;
        private boolean searchResult;
        private String note;
        private String creationDate;

        public HighlightAnnotation(int annotationIndex, String groupKey, RectF bounds, String quote) {
            this(annotationIndex, groupKey, bounds, quote, 0xFFFFFF00, false);
        }

        public HighlightAnnotation(int annotationIndex, String groupKey, RectF bounds, String quote, int color) {
            this(annotationIndex, groupKey, bounds, quote, color, false);
        }

        public HighlightAnnotation(int annotationIndex, String groupKey, RectF bounds, String quote,
                                   int color, boolean appOwned) {
            this(annotationIndex, groupKey, bounds, quote, color, appOwned, null, null);
        }

        public HighlightAnnotation(int annotationIndex, String groupKey, RectF bounds, String quote,
                                   int color, boolean appOwned, String note, String creationDate) {
            this(annotationIndex, groupKey, bounds, quote, color, appOwned, false, note, creationDate);
        }

        public HighlightAnnotation(int annotationIndex, String groupKey, RectF bounds, String quote,
                                   int color, boolean appOwned, boolean searchResult,
                                   String note, String creationDate) {
            this.annotationIndex = annotationIndex;
            this.groupKey = groupKey == null ? "" : groupKey;
            this.bounds = bounds;
            this.quote = quote == null ? "" : quote;
            this.color = color;
            this.appOwned = appOwned;
            this.searchResult = searchResult;
            this.note = note == null ? "" : note;
            this.creationDate = creationDate == null ? "" : creationDate;
        }

        public int getAnnotationIndex() {
            return annotationIndex;
        }

        public String getGroupKey() {
            return groupKey;
        }

        public RectF getBounds() {
            return bounds;
        }

        public String getQuote() {
            return quote;
        }

        public int getColor() {
            return color;
        }

        public boolean isAppOwned() {
            return appOwned;
        }

        public boolean isSearchResult() {
            return searchResult;
        }

        public String getNote() {
            return note;
        }

        public String getCreationDate() {
            return creationDate;
        }
    }

    public static class FontInfo {
        private final String name;
        private final boolean embedded;

        public FontInfo(String name, boolean embedded) {
            this.name = name == null ? "" : name;
            this.embedded = embedded;
        }

        public String getName() {
            return name;
        }

        public boolean isEmbedded() {
            return embedded;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof FontInfo)) {
                return false;
            }
            FontInfo that = (FontInfo) other;
            return embedded == that.embedded && name.equals(that.name);
        }

        @Override
        public int hashCode() {
            return name.hashCode() * 31 + (embedded ? 1 : 0);
        }
    }

    public static class FormField {
        public static final int TYPE_CHECKBOX = 2;
        public static final int TYPE_RADIO_BUTTON = 3;
        public static final int TYPE_TEXT_FIELD = 6;

        public static final int FLAG_READONLY = 1;
        public static final int FLAG_TEXT_MULTILINE = 1 << 12;

        private final int annotationIndex;
        private final int type;
        private final int flags;
        private final String name;
        private final String alternateName;
        private final String value;
        private final boolean checked;

        public FormField(int annotationIndex, int type, int flags, String name, String alternateName, String value, boolean checked) {
            this.annotationIndex = annotationIndex;
            this.type = type;
            this.flags = flags;
            this.name = name == null ? "" : name;
            this.alternateName = alternateName == null ? "" : alternateName;
            this.value = value == null ? "" : value;
            this.checked = checked;
        }

        public int getAnnotationIndex() {
            return annotationIndex;
        }

        public int getType() {
            return type;
        }

        public String getName() {
            return name;
        }

        public String getAlternateName() {
            return alternateName;
        }

        public String getValue() {
            return value;
        }

        public boolean isChecked() {
            return checked;
        }

        public boolean isReadOnly() {
            return (flags & FLAG_READONLY) != 0;
        }

        public boolean isMultiline() {
            return (flags & FLAG_TEXT_MULTILINE) != 0;
        }
    }

    /*package*/ PdfDocument() {
    }

    /*package*/ long mNativeDocPtr;
    /*package*/ ParcelFileDescriptor parcelFileDescriptor;
    /*package*/ volatile boolean closed = false;

    /*package*/ final Map<Integer, Long> mNativePagesPtr = new ArrayMap<>();
    /*package*/ final Map<Integer, Long> mNativeTextPagesPtr = new ArrayMap<>();

    public boolean isClosed() {
        return closed;
    }

    public boolean hasPage(int index) {
        return mNativePagesPtr.containsKey(index);
    }
}
