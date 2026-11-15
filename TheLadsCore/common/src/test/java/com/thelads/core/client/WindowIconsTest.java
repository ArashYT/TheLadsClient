package com.thelads.core.client;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class WindowIconsTest {
    @Test void everyVanillaIconSizeIsASquarePngOfThatSize() throws Exception {
        assertTrue(WindowIcons.available());
        for (int size : WindowIcons.SIZES) {
            try (InputStream in = WindowIcons.open(size)) {
                BufferedImage image = ImageIO.read(in);
                assertNotNull(image, "decodes " + size);
                assertEquals(size, image.getWidth());
                assertEquals(size, image.getHeight());
            }
        }
    }

    /** Every size is the 256 px artwork scaled down smoothly (Lanczos), not picked pixel by pixel: it matches an area average of it. */
    @Test void smallerSizesAreSmoothDownscalesOfTheLargest() throws Exception {
        BufferedImage large;
        try (InputStream in = WindowIcons.open(256)) { large = ImageIO.read(in); }
        for (int size : WindowIcons.SIZES) {
            if (size == 256) continue;
            BufferedImage icon;
            try (InputStream in = WindowIcons.open(size)) { icon = ImageIO.read(in); }
            int block = 256 / size;
            long error = 0;
            for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
                int[] mean = new int[3];
                // The area average over the pixels this one stands for (the exact box is only equal for sizes that divide 256, so use the span).
                int x0 = x * 256 / size, x1 = Math.max(x0 + 1, (x + 1) * 256 / size), y0 = y * 256 / size, y1 = Math.max(y0 + 1, (y + 1) * 256 / size), n = 0;
                for (int yy = y0; yy < y1; yy++) for (int xx = x0; xx < x1; xx++) {
                    int rgb = large.getRGB(xx, yy);
                    mean[0] += rgb >> 16 & 255; mean[1] += rgb >> 8 & 255; mean[2] += rgb & 255; n++;
                }
                int rgb = icon.getRGB(x, y);
                error += Math.abs((rgb >> 16 & 255) - mean[0] / n) + Math.abs((rgb >> 8 & 255) - mean[1] / n) + Math.abs((rgb & 255) - mean[2] / n);
            }
            double perChannel = error / (3.0 * size * size);
            // This artwork scaled to 16 px pixel by pixel is off by 17 per channel (7 at 128 px); a smooth downscale is off by 4 or less.
            assertTrue(perChannel < 5, size + " px icon differs " + perChannel + " per channel from a smooth downscale (block " + block + ")");
        }
    }
}
