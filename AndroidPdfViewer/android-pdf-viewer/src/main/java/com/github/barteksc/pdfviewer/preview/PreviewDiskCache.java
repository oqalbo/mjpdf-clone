// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer.preview;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class PreviewDiskCache {

    private final File root;
    private final Set<String> writing = new HashSet<>();

    PreviewDiskCache(File root) {
        this.root = root;
    }

    void write(String docKey, PreviewKey key, byte[] data) {
        if (data == null) {
            return;
        }
        File file = fileFor(docKey, key);
        String path = file.getAbsolutePath();
        synchronized (writing) {
            if (!writing.add(path)) {
                return;
            }
        }
        File parent = file.getParentFile();
        File temp = parent == null ? new File(file.getName() + ".tmp") : new File(parent, file.getName() + ".tmp");
        try {
            if (parent != null) {
                parent.mkdirs();
            }
            FileOutputStream stream = new FileOutputStream(temp);
            try {
                stream.write(data);
                stream.flush();
            } finally {
                stream.close();
            }
            if (!temp.renameTo(file)) {
                temp.delete();
            }
        } catch (IOException e) {
            temp.delete();
        } finally {
            synchronized (writing) {
                writing.remove(path);
            }
        }
    }

    byte[] read(String docKey, PreviewKey key) {
        File file = fileFor(docKey, key);
        if (!file.exists()) {
            return null;
        }
        try {
            byte[] data = readAll(file);
            file.setLastModified(System.currentTimeMillis());
            return data;
        } catch (IOException e) {
            return null;
        }
    }

    void delete(String docKey, PreviewKey key) {
        fileFor(docKey, key).delete();
    }

    boolean exists(String docKey, PreviewKey key) {
        return fileFor(docKey, key).exists();
    }

    void trim(long budgetBytes) {
        List<TrimEntry> files = new ArrayList<>();
        File[] docDirs = root.listFiles();
        if (docDirs != null) {
            for (File docDir : docDirs) {
                if (!docDir.isDirectory()) {
                    continue;
                }
                File[] pageFiles = docDir.listFiles();
                if (pageFiles == null) {
                    continue;
                }
                for (File pageFile : pageFiles) {
                    if (pageFile.isFile() && !pageFile.getName().endsWith(".tmp")) {
                        files.add(new TrimEntry(pageFile));
                    }
                }
            }
        }
        Collections.sort(files, new Comparator<TrimEntry>() {
            @Override
            public int compare(TrimEntry left, TrimEntry right) {
                return Long.compare(left.modified, right.modified);
            }
        });
        long total = 0;
        for (TrimEntry entry : files) {
            total += entry.length;
        }
        for (TrimEntry entry : files) {
            if (total <= budgetBytes) {
                break;
            }
            if (entry.file.delete()) {
                total -= entry.length;
            }
        }
    }

    File fileFor(String docKey, PreviewKey key) {
        return new File(new File(root, docKey),
                "p" + key.page + "-w" + key.bucketWidthPx + "-t" + key.tag + ".jpg");
    }

    private static byte[] readAll(File file) throws IOException {
        long length = file.length();
        int initial = length > 0 && length < Integer.MAX_VALUE ? (int) length : 32;
        ByteArrayOutputStream out = new ByteArrayOutputStream(initial);
        FileInputStream in = new FileInputStream(file);
        try {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } finally {
            in.close();
        }
        return out.toByteArray();
    }

    private static final class TrimEntry {
        final File file;
        final long modified;
        final long length;

        TrimEntry(File file) {
            this.file = file;
            this.modified = file.lastModified();
            this.length = file.length();
        }
    }
}
