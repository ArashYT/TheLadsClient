/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 */
package com.thelads.core.features.decentscreenshot;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;

public class ScreenshotFinder {
    private static final String[] SUPPORTED_EXTENSIONS = new String[]{".png", ".jpg", ".jpeg"};

    public static List<File> getScreenshots() {
        File screenshotDir = new File(Minecraft.getInstance().gameDirectory, "screenshots");
        ArrayList<File> results = new ArrayList<File>();
        if (!screenshotDir.exists() || !screenshotDir.isDirectory()) {
            return results;
        }
        File[] files = screenshotDir.listFiles(file -> {
            if (!file.isFile()) {
                return false;
            }
            String name = file.getName().toLowerCase();
            for (String ext : SUPPORTED_EXTENSIONS) {
                if (!name.endsWith(ext)) continue;
                return true;
            }
            return false;
        });
        if (files != null) {
            Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
            results.addAll(Arrays.asList(files));
        }
        return results;
    }

    public static File getScreenshotDirectory() {
        File dir = new File(Minecraft.getInstance().gameDirectory, "screenshots");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }
}

