package com.thelads.core.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Limits for what a remote cape provider (OptiFine, LabyMod, MinecraftCapes, Cosmetica) may send to the embedded Capes mod, checked
 * before anything is decoded or uploaded: a hostile or hijacked endpoint (OptiFine's is plain http) must not cost memory or a long
 * freeze with a huge image or an endless body. No Minecraft types, so both game versions share it.
 */
public final class CapeImageGuard {
    /** A cape PNG is a few KB (up to a few hundred KB for HD and animated ones). */
    public static final int MAX_IMAGE_BYTES = 2 * 1024 * 1024;
    public static final int MAX_WIDTH = 2048;
    /** Static capes are at most 2048x1024 (canvas doubles to fit). */
    public static final int MAX_STATIC_HEIGHT = 1024;
    public static final int MAX_HEIGHT = 1024;
    public static final int MAX_FRAMES = 256;
    public static final int MIN_ANIMATED_WIDTH = 16;
    public static final int MAX_PIXELS = 4 * 1024 * 1024;
    /** The JSON a provider answers with before the image URL. */
    public static final int MAX_JSON_BYTES = 64 * 1024;

    private static final int[] PNG_SIGNATURE = {0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private CapeImageGuard() {
    }

    /** The PNG bytes of {@code in}, or null when it is longer than {@link #MAX_IMAGE_BYTES}, not a PNG, or its size is out of bounds. */
    public static byte[] readPng(InputStream in) throws IOException {
        return readPng(in, false);
    }

    public static byte[] readPng(InputStream in, boolean animated) throws IOException {
        byte[] bytes = readLimited(in, MAX_IMAGE_BYTES);
        return bytes != null && acceptablePng(bytes, animated) ? bytes : null;
    }

    /** The UTF-8 text of {@code in}, or null when it is longer than {@link #MAX_JSON_BYTES}. */
    public static String readJson(InputStream in) throws IOException {
        byte[] bytes = readLimited(in, MAX_JSON_BYTES);
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    /** True for a static PNG whose header is in bounds. */
    public static boolean acceptablePng(byte[] png) {
        return acceptablePng(png, false);
    }

    /** True for a PNG whose header (signature, then the IHDR chunk with the width and height) is in bounds. */
    public static boolean acceptablePng(byte[] png, boolean animated) {
        if (png.length < 24) {
            return false;
        }
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if ((png[i] & 0xFF) != PNG_SIGNATURE[i]) {
                return false;
            }
        }
        if (png[12] != 'I' || png[13] != 'H' || png[14] != 'D' || png[15] != 'R') {
            return false;
        }
        long width = uint32(png, 16), height = uint32(png, 20);
        return fits((int) width, (int) height, animated);
    }

    /**
     * Static: height <= 1024 and width <= 2048.
     * Animated: frames <= 256 and width >= 16.
     */
    public static boolean fits(int width, int height, boolean animated) {
        if (width < 1 || height < 1 || width > MAX_WIDTH || (long) width * height > MAX_PIXELS) {
            return false;
        }
        if (!animated) {
            return width <= MAX_WIDTH && height <= MAX_STATIC_HEIGHT;
        } else {
            if (width < MIN_ANIMATED_WIDTH) {
                return false;
            }
            int frames = frameCount(width, height);
            return frames >= 1 && frames <= MAX_FRAMES;
        }
    }

    /** Frames in an animated cape image (each frame is width x width/2, stacked); 0 when it is too small to hold one. */
    public static int frameCount(int width, int height) {
        int frameHeight = width / 2;
        return frameHeight <= 0 ? 0 : height / frameHeight;
    }

    /** True for an https URL: provider answers name the cape image's address, and http would let anyone on the path swap it. */
    public static boolean isHttps(String url) {
        return url != null && url.regionMatches(true, 0, "https://", 0, 8);
    }

    private static byte[] readLimited(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            if (out.size() + read > limit) {
                return null;
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static long uint32(byte[] b, int at) {
        return ((long) (b[at] & 0xFF) << 24) | ((b[at + 1] & 0xFF) << 16) | ((b[at + 2] & 0xFF) << 8) | (b[at + 3] & 0xFF);
    }
}
