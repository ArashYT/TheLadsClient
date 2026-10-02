package com.thelads.core.v1_8_9.feature;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lunar Client's 1.8 resource packs (&lt;Lunar&gt;/profiles/1.8/resourcepacks) are listed beside the shared ones, read in place.
 * The launcher passes Lunar's folder as LADS_LUNAR_DIR (a sandbox in QA); without it, the user's .lunarclient.
 */
public final class LunarPacks189 {
    private LunarPacks189() {}

    static File folder() {
        String root = System.getenv("LADS_LUNAR_DIR");
        File lunar = root == null || root.trim().isEmpty() ? new File(System.getProperty("user.home"), ".lunarclient") : new File(root.trim());
        return new File(lunar, "profiles" + File.separator + "1.8" + File.separator + "resourcepacks");
    }

    /** ResourcePackRepository.getResourcePackFiles RETURN: Lunar's packs added, a pack name already listed wins. */
    public static List<File> withLunar(List<File> shared) {
        File[] lunar = folder().listFiles(file -> file.isFile() && file.getName().endsWith(".zip")
            || file.isDirectory() && new File(file, "pack.mcmeta").isFile());
        if (lunar == null || lunar.length == 0) return shared;
        Set<String> names = new HashSet<>();
        for (File file : shared) names.add(file.getName());
        List<File> all = new ArrayList<>(shared);
        for (File file : lunar) if (names.add(file.getName())) all.add(file);
        return all;
    }
}
