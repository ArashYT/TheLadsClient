package com.thelads.core.client;

import static org.junit.jupiter.api.Assertions.*;

import com.thelads.core.modules.CrosshairModule;
import org.junit.jupiter.api.Test;

class CrosshairBackgroundTest {
    @Test void everyBiomeAndTimeHasItsScreenshot() {
        long bytes = 0;
        int pictures = 0;
        for (int biome = 0; biome < CrosshairBackground.BIOMES.length; biome++)
            for (int time = 0; time < CrosshairBackground.TIMES.length; time++) {
                String path = CrosshairBackground.picture(biome, time);
                var picture = CrosshairBackground.read(path);
                assertNotNull(picture, path);
                assertTrue(picture.width >= 320 && picture.height >= 180, path);
                if (CrosshairBackground.timeless(biome) && time > 0) continue;
                pictures++;
                try (var in = CrosshairBackground.class.getResourceAsStream(path)) { bytes += in.readAllBytes().length; }
                catch (java.io.IOException failure) { fail(failure); }
            }
        assertTrue(bytes <= 6L << 20, pictures + " pictures, " + bytes + " bytes");
    }

    @Test void timelessBiomesShareOnePicture() {
        int nether = java.util.Arrays.asList(CrosshairBackground.BIOMES).indexOf("nether_wastes");
        assertTrue(CrosshairBackground.timeless(nether));
        assertEquals(CrosshairBackground.picture(nether, 0), CrosshairBackground.picture(nether, 5));
        assertTrue(CrosshairBackground.picture(CrosshairBackground.DEFAULT_BIOME, CrosshairBackground.DEFAULT_TIME).endsWith("cherry_grove_noon.jpg"));
        assertEquals("Old Growth Spruce Taiga", CrosshairBackground.biomeNames()[java.util.Arrays.asList(CrosshairBackground.BIOMES).indexOf("old_growth_spruce_taiga")]);
    }

    @Test void coverCropsToTheBoxShapeCentred() {
        assertArrayEquals(new int[] {50, 0, 300, 300}, CrosshairBackground.cover(400, 300, 100, 100)); // square box: the sides go
        assertArrayEquals(new int[] {0, 75, 400, 150}, CrosshairBackground.cover(400, 300, 200, 75)); // wide box: top and bottom go
        assertArrayEquals(new int[] {0, 0, 400, 300}, CrosshairBackground.cover(400, 300, 4, 3));
    }

    @Test void gradientRunsFromFirstToLastColour() {
        assertEquals(0x102030, CrosshairBackground.mix(0x102030, 0xffffff, 0));
        assertEquals(0xffffff, CrosshairBackground.mix(0x102030, 0xffffff, 1));
        assertEquals(0x808080, CrosshairBackground.mix(0x000000, 0xffffff, .5));
    }

    @Test void ownPicturesAreScaledAndHugeOnesRefused(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        var photo = dir.resolve("photo.png").toFile();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(3000, 1500, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", photo);
        var picture = CrosshairBackground.read(photo.getPath());
        assertNotNull(picture);
        assertEquals(CrosshairBackground.MAX_SIDE, picture.width);
        assertEquals(CrosshairBackground.MAX_SIDE / 2, picture.height);
        // A PNG whose header claims 20000 x 20000 (400 megapixels) is refused before anything is decoded.
        var bomb = new java.io.ByteArrayOutputStream();
        var ihdr = java.nio.ByteBuffer.allocate(17).put("IHDR".getBytes()).putInt(20000).putInt(20000).put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0).array();
        var crc = new java.util.zip.CRC32();
        crc.update(ihdr);
        bomb.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 0x1a, 10, 0, 0, 0, 13});
        bomb.write(ihdr);
        bomb.write(java.nio.ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
        var file = dir.resolve("bomb.png");
        java.nio.file.Files.write(file, bomb.toByteArray());
        assertNull(CrosshairBackground.read(file.toString()));
        assertNull(CrosshairBackground.read(dir.resolve("missing.png").toString()));
    }

    @Test void onlyTheChosenKindsOptionsShow() {
        var module = new CrosshairModule();
        assertTrue(module.hidden(module.image));
        assertFalse(module.hidden(module.biome));
        assertTrue(module.hidden(module.gradientStart));
        module.background.setIndex(CrosshairBackground.GRADIENT);
        assertFalse(module.hidden(module.gradientEnd));
        assertTrue(module.hidden(module.biome));
        module.background.setIndex(CrosshairBackground.LIVE);
        assertFalse(module.hidden(module.time));
        module.biome.setIndex(java.util.Arrays.asList(CrosshairBackground.BIOMES).indexOf("the_end"));
        assertTrue(module.hidden(module.time));
        module.background.setIndex(CrosshairBackground.IMAGE);
        assertFalse(module.hidden(module.chooseImage));
    }
}
