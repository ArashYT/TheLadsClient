package com.thelads.core.v1_20_1.adapter;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/** Pixel size of a PNG resource (read from its header once), for blits that need the texture's own width and height. */
public final class TextureSizes1201 {
    private static final Map<ResourceLocation, int[]> SIZES = new HashMap<>();
    private static final int[] MISSING = new int[0];

    private TextureSizes1201() {}

    /** {width, height}, or null while the resource does not exist (a resource pack reload clears the memory). */
    public static synchronized int[] of(ResourceLocation texture) {
        int[] known = SIZES.get(texture);
        if (known != null) return known == MISSING ? null : known;
        int[] found = MISSING;
        var resource = Minecraft.getInstance().getResourceManager().getResource(texture);
        if (resource.isPresent()) {
            try (InputStream in = resource.get().open()) {
                byte[] header = in.readNBytes(24);
                if (header.length == 24) {
                    int width = (header[16] & 255) << 24 | (header[17] & 255) << 16 | (header[18] & 255) << 8 | header[19] & 255;
                    int height = (header[20] & 255) << 24 | (header[21] & 255) << 16 | (header[22] & 255) << 8 | header[23] & 255;
                    if (width > 0 && height > 0) found = new int[] {width, height};
                }
            } catch (java.io.IOException ignored) {}
        }
        SIZES.put(texture, found);
        return found == MISSING ? null : found;
    }

    public static synchronized void clear() {
        SIZES.clear();
    }
}
