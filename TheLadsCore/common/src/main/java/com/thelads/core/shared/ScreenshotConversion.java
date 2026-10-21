package com.thelads.core.shared;

public final class ScreenshotConversion {
    private ScreenshotConversion() {}

    /**
     * Flips raw OpenGL pixels vertically (and drops the texture's row padding). They are read as GL_BGRA +
     * GL_UNSIGNED_INT_8_8_8_8_REV, which already gives ARGB ints, so like vanilla's TextureUtil.processPixelValues no channel is swapped.
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
                out[outRow + x] = 0xFF000000 | raw[rawRow + x];
            }
        }
    }
}
