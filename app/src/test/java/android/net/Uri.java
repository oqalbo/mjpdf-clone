// Written by Mudlej. License is GPLv3.
package android.net;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class Uri {
    private static final Pattern PARTS = Pattern.compile(
            "^(?:([A-Za-z][A-Za-z0-9+.-]*):)?(?:/{2}([^/?#]*))?([^?#]*)(?:\\?([^#]*))?(?:#(.*))?$",
            Pattern.DOTALL);

    private Uri() {}

    public static Uri parse(String value) { return new Parsed(Objects.requireNonNull(value)); }
    public static Uri fromFile(File file) {
        return new Builder().scheme("file").authority("").path(file.getAbsolutePath()).build();
    }
    public static String encode(String value) {
        if (value == null) return null;
        StringBuilder result = new StringBuilder();
        for (byte item : value.getBytes(StandardCharsets.UTF_8)) {
            int b = item & 255;
            if ((b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z')
                    || (b >= '0' && b <= '9') || "-._~!*'()".indexOf(b) >= 0) {
                result.append((char) b);
            } else {
                result.append('%').append("0123456789ABCDEF".charAt(b >> 4))
                        .append("0123456789ABCDEF".charAt(b & 15));
            }
        }
        return result.toString();
    }
    public static String decode(String value) {
        if (value == null) return null;
        StringBuilder result = new StringBuilder(value.length());
        java.io.ByteArrayOutputStream pending = new java.io.ByteArrayOutputStream();
        int index = 0;
        while (index < value.length()) {
            char c = value.charAt(index);
            if (c == '%' && index + 2 < value.length() + 0
                    && Character.digit(value.charAt(index + 1), 16) >= 0
                    && Character.digit(value.charAt(index + 2), 16) >= 0) {
                pending.write(Integer.parseInt(value.substring(index + 1, index + 3), 16));
                index += 3;
                continue;
            }
            if (pending.size() > 0) {
                result.append(new String(pending.toByteArray(), StandardCharsets.UTF_8));
                pending.reset();
            }
            if (c == '%') {
                result.append('�');
                index += 1;
                continue;
            }
            result.append(c);
            index += 1;
        }
        if (pending.size() > 0) result.append(new String(pending.toByteArray(), StandardCharsets.UTF_8));
        return result.toString();
    }
    public abstract String getScheme();
    public abstract String getSchemeSpecificPart();
    public abstract String getAuthority();
    public abstract String getPath();
    public abstract List<String> getPathSegments();
    public String getLastPathSegment() {
        List<String> segments = getPathSegments();
        return segments.isEmpty() ? null : segments.get(segments.size() - 1);
    }
    public abstract String getQuery();
    public abstract String getFragment();
    public boolean isAbsolute() { return getScheme() != null; }
    public boolean isRelative() { return !isAbsolute(); }
    public abstract boolean isHierarchical();
    public boolean isOpaque() { return !isHierarchical(); }
    public Uri normalizeScheme() {
        String scheme = getScheme();
        return scheme == null ? this : parse(scheme.toLowerCase(Locale.ROOT)
                + toString().substring(scheme.length()));
    }
    @Override public abstract String toString();
    @Override public boolean equals(Object other) {
        return other instanceof Uri && toString().equals(other.toString());
    }
    @Override public int hashCode() { return toString().hashCode(); }

    private static final class Parsed extends Uri {
        private final String value;
        private final Matcher parts;

        private Parsed(String value) {
            this.value = value;
            parts = PARTS.matcher(value);
            if (!parts.matches()) throw new IllegalArgumentException(value);
        }
        @Override public String getScheme() { return parts.group(1); }
        @Override public String getSchemeSpecificPart() {
            int start = getScheme() == null ? 0 : getScheme().length() + 1;
            int end = value.indexOf('#', start);
            return decode(value.substring(start, end < 0 ? value.length() : end));
        }
        @Override public String getAuthority() { return isOpaque() ? null : decode(parts.group(2)); }
        @Override public String getPath() { return isOpaque() ? null : decode(parts.group(3)); }
        @Override public List<String> getPathSegments() {
            if (isOpaque()) return Collections.emptyList();
            List<String> result = new ArrayList<>();
            for (String segment : parts.group(3).split("/")) {
                if (!segment.isEmpty()) result.add(decode(segment));
            }
            return Collections.unmodifiableList(result);
        }
        @Override public String getQuery() { return isOpaque() ? null : decode(parts.group(4)); }
        @Override public String getFragment() { return decode(parts.group(5)); }
        @Override public boolean isHierarchical() {
            return isRelative() || value.startsWith("/", getScheme().length() + 1);
        }
        @Override public String toString() { return value; }
    }

    public static final class Builder {
        private String scheme;
        private String authority;
        private String path = "";

        public Builder scheme(String value) { scheme = value; return this; }
        public Builder authority(String value) {
            authority = value == null ? null : encode(value).replace("%3A", ":")
                    .replace("%40", "@").replace("%5B", "[").replace("%5D", "]");
            return this;
        }
        public Builder path(String value) {
            path = value == null ? "" : encode(value).replace("%2F", "/");
            return this;
        }
        public Builder appendPath(String value) { return appendEncodedPath(encode(value)); }
        public Builder appendEncodedPath(String value) {
            if (value != null && !value.isEmpty()) {
                path += (path.endsWith("/") || value.startsWith("/") ? "" : "/") + value;
            }
            return this;
        }
        public Uri build() {
            String prefix = scheme == null ? "" : scheme + ":";
            if (authority != null) prefix += "//" + authority;
            String absolute = !path.isEmpty() && !path.startsWith("/")
                    && (scheme != null || authority != null) ? "/" : "";
            return parse(prefix + absolute + path);
        }
    }
}
