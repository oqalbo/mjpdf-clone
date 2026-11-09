package com.github.barteksc.pdfviewer.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.TreeMap;

/**
 * Crop data for a PDF document. A document can use one crop for all pages, or
 * separate crops for zero-based even/odd document page indexes to handle alternating book gutters.
 */
public final class CropMargins {

    public static final int STORAGE_VERSION = 6;

    private static final String PART_SEPARATOR = ";";
    private static final String PAGE_CROP_SEPARATOR = "|";
    private static final String PAGE_CROP_VALUE_SEPARATOR = ":";

    private final int version;
    private final CropBounds evenCrop;
    private final CropBounds oddCrop;
    private final Map<Integer, CropBounds> pageCrops;

    public CropMargins(CropBounds evenCrop, CropBounds oddCrop) {
        this(STORAGE_VERSION, evenCrop, oddCrop, Collections.emptyMap());
    }

    public CropMargins(CropBounds evenCrop, CropBounds oddCrop, Map<Integer, CropBounds> pageCrops) {
        this(STORAGE_VERSION, evenCrop, oddCrop, pageCrops);
    }

    private CropMargins(int version, CropBounds evenCrop, CropBounds oddCrop, Map<Integer, CropBounds> pageCrops) {
        this.version = version;
        this.evenCrop = evenCrop == null ? CropBounds.fullPage() : evenCrop;
        this.oddCrop = oddCrop == null ? this.evenCrop : oddCrop;
        this.pageCrops = sanitizePageCrops(pageCrops);
    }

    public static CropMargins fullPage() {
        return new CropMargins(CropBounds.fullPage(), CropBounds.fullPage());
    }

    public static CropMargins single(CropBounds cropBounds) {
        CropBounds crop = cropBounds == null ? CropBounds.fullPage() : cropBounds;
        return new CropMargins(crop, crop);
    }

    public static CropMargins fromStorageString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        String[] parts = value.split(PART_SEPARATOR, -1);
        if (parts.length != 4) {
            return null;
        }

        try {
            int version = Integer.parseInt(parts[0]);
            if (version != STORAGE_VERSION) {
                return null;
            }
            CropBounds evenCrop = CropBounds.fromStorageString(parts[1]);
            CropBounds oddCrop = CropBounds.fromStorageString(parts[2]);
            if (evenCrop == null || oddCrop == null) {
                return null;
            }
            Map<Integer, CropBounds> pageCrops = pageCropsFromStorageString(parts[3]);
            if (pageCrops == null) {
                return null;
            }
            return new CropMargins(version, evenCrop, oddCrop, pageCrops);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public int getVersion() {
        return version;
    }

    public CropBounds getEvenCrop() {
        return evenCrop;
    }

    public CropBounds getOddCrop() {
        return oddCrop;
    }

    public Map<Integer, CropBounds> getPageCrops() {
        return pageCrops;
    }

    public Set<Integer> getExcludedPages() {
        Set<Integer> excludedPages = new TreeSet<>();
        for (Map.Entry<Integer, CropBounds> entry : pageCrops.entrySet()) {
            if (entry.getValue().isFullPage()) {
                excludedPages.add(entry.getKey());
            }
        }
        return Collections.unmodifiableSet(excludedPages);
    }

    /**
     * Return the crop for a zero-based PDF document page index.
     */
    public CropBounds forDocumentPage(int documentPage) {
        CropBounds pageCrop = pageCrops.get(documentPage);
        if (pageCrop != null) {
            return pageCrop;
        }
        return (documentPage & 1) == 0 ? evenCrop : oddCrop;
    }

    public boolean isFullPage() {
        return evenCrop.isFullPage() && oddCrop.isFullPage() && pageCrops.isEmpty();
    }

    public boolean hasParitySplit() {
        return !evenCrop.isSimilarTo(oddCrop, 0.0001f);
    }

    public String toStorageString() {
        return version
                + PART_SEPARATOR + evenCrop.toStorageString()
                + PART_SEPARATOR + oddCrop.toStorageString()
                + PART_SEPARATOR + pageCropsToStorageString();
    }

    private String pageCropsToStorageString() {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Map.Entry<Integer, CropBounds> entry : pageCrops.entrySet()) {
            if (!first) {
                builder.append(PAGE_CROP_SEPARATOR);
            }
            builder.append(entry.getKey());
            builder.append(PAGE_CROP_VALUE_SEPARATOR);
            builder.append(entry.getValue().toStorageString());
            first = false;
        }
        return builder.toString();
    }

    private static Map<Integer, CropBounds> pageCropsFromStorageString(String value) {
        Map<Integer, CropBounds> pageCrops = new HashMap<>();
        if (value == null || value.isEmpty()) {
            return pageCrops;
        }
        String[] entries = value.split("\\" + PAGE_CROP_SEPARATOR, -1);
        for (String entry : entries) {
            if (entry.isEmpty() || !entry.equals(entry.trim())) {
                return null;
            }
            int separatorIndex = entry.indexOf(PAGE_CROP_VALUE_SEPARATOR);
            if (separatorIndex <= 0 || separatorIndex >= entry.length() - 1) {
                return null;
            }
            int pageIndex = Integer.parseInt(entry.substring(0, separatorIndex));
            if (pageIndex < 0) {
                return null;
            }
            CropBounds cropBounds = CropBounds.fromStorageString(entry.substring(separatorIndex + 1));
            if (cropBounds == null) {
                return null;
            }
            pageCrops.put(pageIndex, cropBounds);
        }
        return pageCrops;
    }

    private static Map<Integer, CropBounds> sanitizePageCrops(Map<Integer, CropBounds> pageCrops) {
        if (pageCrops == null || pageCrops.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Integer, CropBounds> sanitized = new TreeMap<>();
        for (Map.Entry<Integer, CropBounds> entry : pageCrops.entrySet()) {
            Integer page = entry.getKey();
            CropBounds cropBounds = entry.getValue();
            if (page != null && page >= 0 && cropBounds != null) {
                sanitized.put(page, cropBounds);
            }
        }
        return Collections.unmodifiableMap(sanitized);
    }
}
