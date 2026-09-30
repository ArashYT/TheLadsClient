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
}
