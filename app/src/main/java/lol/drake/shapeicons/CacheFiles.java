package lol.drake.shapeicons;

import android.content.Context;

import java.io.File;
import java.io.FileNotFoundException;

/** Small helpers for the provider cache folders: safe names, bounded size. */
final class CacheFiles {
    static final int MAX_FILES = 400;                 // per folder; oldest are deleted first
    static final long MAX_AGE_MS = 24L * 3600 * 1000; // anything older than a day is stale anyway

    private CacheFiles() { }

    static File dir(Context c, String name) {
        File d = new File(c.getCacheDir(), name);
        if (!d.isDirectory() && !d.mkdirs() && !d.isDirectory()) throw new IllegalStateException("cannot create " + d);
        return d;
    }

    /** Only plain file names: callers (launchers) control these strings. */
    static String safe(String n) throws FileNotFoundException {
        if (n == null || !n.matches("[A-Za-z0-9._-]{1,128}") || n.contains("..")) throw new FileNotFoundException("bad name");
        return n;
    }

    /** Deletes old files and, above MAX_FILES, the oldest ones. Never throws. */
    static void prune(File dir) {
        try {
            File[] all = dir.listFiles();
            if (all == null) return;
            long now = System.currentTimeMillis();
            java.util.Arrays.sort(all, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            for (int i = 0; i < all.length; i++) {
                if (i >= MAX_FILES || now - all[i].lastModified() > MAX_AGE_MS) //noinspection ResultOfMethodCallIgnored
                    all[i].delete();
            }
        } catch (RuntimeException ignored) { }
    }
}
