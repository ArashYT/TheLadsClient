package com.thelads.core.client;

import java.util.Arrays;

/**
 * Clear Glass: which textures are glass (exact names: glass and the 16 stained glass, not panes' top strip or tinted glass), and the
 * edit. Inside a frame as wide as the texture's 1/16 (one pixel at 16x16), every pixel takes the inside's most common color: fully
 * clear for glass, the tint for stained glass, so the streaks go and the frame stays. Animated textures (a strip of square frames)
 * are edited frame by frame. Runs while textures load, never per frame.
 */
public final class ClearGlass {
    private static final String[] COLORS = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
        "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
    /** 1.8.9's names for the stained glass colors (silver is light gray there). */
    private static final String[] COLORS_189 = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
        "silver", "cyan", "purple", "blue", "brown", "green", "red", "black"};

    private ClearGlass() {}

    /** A modern texture path in the minecraft namespace ("block/glass", "block/red_stained_glass"). */
    public static boolean isGlass(String path) {
        if ("block/glass".equals(path)) return true;
        for (String color : COLORS) if (path.equals("block/" + color + "_stained_glass")) return true;
        return false;
    }

    /** A 1.8.9 sprite name ("minecraft:blocks/glass", "minecraft:blocks/glass_red"). */
    public static boolean isGlass189(String name) {
        if ("minecraft:blocks/glass".equals(name)) return true;
        for (String color : COLORS_189) if (name.equals("minecraft:blocks/glass_" + color)) return true;
        return false;
    }

    /**
     * Edits {@code pixels} (rows of {@code width}, any channel order) frame by frame, each frame {@code width} x {@code frameHeight}.
     * Fully transparent pixels count as one color whatever their RGB.
     */
    public static void clear(int[] pixels, int width, int frameHeight) {
        int border = Math.max(1, width / 16);
        if (width <= 2 * border || frameHeight <= 2 * border) return;
        int[] inside = new int[(width - 2 * border) * (frameHeight - 2 * border)];
        for (int top = 0; top + frameHeight <= pixels.length / width; top += frameHeight) {
            int n = 0;
            for (int y = top + border; y < top + frameHeight - border; y++)
                for (int x = border; x < width - border; x++) inside[n++] = visible(pixels[y * width + x]);
            int fill = mode(inside);
            for (int y = top + border; y < top + frameHeight - border; y++)
                for (int x = border; x < width - border; x++) pixels[y * width + x] = fill;
        }
    }

    /** Alpha is the top byte in both ARGB and ABGR. */
    private static int visible(int pixel) {
        return (pixel >>> 24) == 0 ? 0 : pixel;
    }

    private static int mode(int[] values) {
        int[] sorted = values.clone();
        Arrays.sort(sorted);
        int best = sorted[0], bestRun = 0;
        for (int i = 0, run = 1; i < sorted.length; i++, run++) {
            if (i + 1 < sorted.length && sorted[i + 1] == sorted[i]) continue;
            if (run > bestRun) { bestRun = run; best = sorted[i]; }
            run = 0;
        }
        return best;
    }
}
