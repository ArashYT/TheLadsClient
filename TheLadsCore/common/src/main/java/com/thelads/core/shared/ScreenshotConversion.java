package com.thelads.core.shared;

public final class ScreenshotConversion {
    private ScreenshotConversion() {}

    /**
     * Converts raw OpenGL BGRA pixels to ARGB format and flips the image vertically in a single pass.
     *
     * @param raw      The raw pixel data read from OpenGL (size at least texWidth * height).
     * @param out      The output array (size at least width * height) to receive converted ARGB pixels.
     * @param width    Visible width of the image.
     * @param height   Visible height of the image.
     * @param texWidth Width of the OpenGL texture / buffer (may exceed visible width when framebuffers are padded).
     */
    public static void convertPixels(int[] raw, int[] out, int width, int height, int texWidth) {
        if (raw == null || out == null || width <= 0 || height <= 0 || texWidth <= 0) return;
        for (int y = 0; y < height; y++) {
            int rawRow = (height - 1 - y) * texWidth;
            int outRow = y * width;
            for (int x = 0; x < width; x++) {
                int p = raw[rawRow + x];
                out[outRow + x] = 0xFF000000 | (p & 0x0000FF00) | ((p & 0x000000FF) << 16) | ((p >> 16) & 0x000000FF);
            }
        }
    }
}
