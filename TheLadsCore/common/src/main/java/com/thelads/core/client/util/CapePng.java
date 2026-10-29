package com.thelads.core.client.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Cape images from the embedded Capes providers (OptiFine's only works over plain HTTP): at most 1 MiB, and only sizes a cape can have,
 * judged from the PNG header before anything is decoded. A tiny PNG could otherwise claim a huge size, and the static-cape canvas
 * doubles 64x32 until it covers the image (an 8193x1 PNG became a 512 MiB texture on the render thread).
 */
public final class CapePng {
    public static final int MAX_BYTES = 1024 * 1024, MAX_WIDTH = 2048, MAX_FRAMES = 256;
    static final long MAX_PIXELS = 2048L * 2048;

    private CapePng() {}

    /** The whole PNG (the stream is closed), or null when it is too big, not a PNG, or no cape could have its size. */
    public static byte[] read(InputStream in, boolean animated) throws IOException {
        byte[] png;
        try (in) {
            png = in.readNBytes(MAX_BYTES + 1);
        }
        if (png.length > MAX_BYTES || png.length < 24) return null;
        ByteBuffer header = ByteBuffer.wrap(png);
        if (header.getLong(0) != 0x89504E470D0A1A0AL || header.getInt(12) != 0x49484452) return null; // signature, then IHDR first
        return fits(header.getInt(16), header.getInt(20), animated) ? png : null;
    }

    /**
     * Static: at most 2048x1024 (its canvas then stays that size). Animated: 1 to 256 frames of width x width/2 (none: the frame
     * counter divided by zero every frame), 16 MiB decoded at most.
     */
    static boolean fits(int width, int height, boolean animated) {
        if (width < 2 || height < 1 || width > MAX_WIDTH || (long) width * height > MAX_PIXELS) return false;
        return animated ? height >= width / 2 && height / (width / 2) <= MAX_FRAMES : height <= MAX_WIDTH / 2;
    }
}
