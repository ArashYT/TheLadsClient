package com.thelads.core.client.pack;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/**
 * Warns when a resource pack the player just switched on is large (a zip of 150 MB or more), because every reload then takes
 * noticeably longer. Only the zip's size on disk is read: no pack content is opened or cached.
 */
public final class LargePackWarning {
    public static final long LARGE_BYTES = 150L * 1024 * 1024;

    private LargePackWarning() {}

    /** "Name (312 MB)" when {@code zip} is a file of at least {@link #LARGE_BYTES}, else null. */
    public static String describe(File zip) {
        if (zip == null || !zip.isFile() || zip.length() < LARGE_BYTES) return null;
        return zip.getName() + " (" + (zip.length() / (1024 * 1024)) + " MB)";
    }

    /** The toast text for the large packs among {@code newIds} that were not selected before ({@code file/<name>} ids, 1.20.1+), or null. */
    public static String forAddedPacks(Collection<String> oldIds, List<String> newIds, Path packDir) {
        if (packDir == null) return null;
        StringBuilder out = null;
        for (String id : newIds) {
            if (id == null || !id.startsWith("file/") || oldIds.contains(id)) continue;
            String text = describe(packDir.resolve(id.substring(5)).toFile());
            if (text != null) out = (out == null ? new StringBuilder() : out.append(", ")).append(text);
        }
        return out == null ? null : out + " - large packs make every reload slower.";
    }

    /** Same for 1.8.9, whose ids are plain file names inside {@code packDir}. */
    public static String forAddedPacks189(Collection<String> oldNames, List<String> newNames, File packDir) {
        if (packDir == null) return null;
        StringBuilder out = null;
        for (String name : newNames) {
            if (name == null || oldNames.contains(name)) continue;
            String text = describe(new File(packDir, name));
            if (text != null) out = (out == null ? new StringBuilder() : out.append(", ")).append(text);
        }
        return out == null ? null : out + " - large packs make every reload slower.";
    }
}
