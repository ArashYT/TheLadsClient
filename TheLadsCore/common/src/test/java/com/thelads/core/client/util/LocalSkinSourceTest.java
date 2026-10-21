package com.thelads.core.client.util;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalSkinSourceTest {
    private static byte[] png(int width, int height) {
        byte[] bytes = new byte[33];
        System.arraycopy(new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10}, 0, bytes, 0, 8);
        java.nio.ByteBuffer.wrap(bytes).putInt(16, width).putInt(20, height);
        return bytes;
    }

    private static String profile(String texturesJson) {
        String value = Base64.getEncoder().encodeToString(texturesJson.getBytes(StandardCharsets.UTF_8));
        return "{\"id\":\"069a79f444e94726a5befca90e38aaf5\",\"name\":\"Notch\",\"properties\":[{\"name\":\"textures\",\"value\":\"" + value + "\"}]}";
    }

    @Test void onlyA64x64Or64x32PngIsASkin() throws IOException {
        LocalSkinSource.validate(png(64, 64));
        LocalSkinSource.validate(png(64, 32));
        assertEquals("Skin must be 64 x 64 or 64 x 32 pixels", assertThrows(IOException.class, () -> LocalSkinSource.validate(png(128, 128))).getMessage());
        assertEquals("Skin must be 64 x 64 or 64 x 32 pixels", assertThrows(IOException.class, () -> LocalSkinSource.validate(png(64, 48))).getMessage());
        assertEquals("Select a PNG skin", assertThrows(IOException.class, () -> LocalSkinSource.validate("not a picture at all, sorry..".getBytes(StandardCharsets.UTF_8))).getMessage());
        assertEquals("Select a PNG skin", assertThrows(IOException.class, () -> LocalSkinSource.validate(new byte[5])).getMessage());
    }

    @Test void aProfileNamesItsSkinAndModel() throws IOException {
        var classic = LocalSkinSource.texture(profile("{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/abc\"}}}"));
        assertEquals("http://textures.minecraft.net/texture/abc", classic.url());
        assertFalse(classic.slim());
        var slim = LocalSkinSource.texture(profile("{\"textures\":{\"SKIN\":{\"url\":\"http://x/y\",\"metadata\":{\"model\":\"slim\"}},\"CAPE\":{\"url\":\"http://x/cape\"}}}"));
        assertTrue(slim.slim());
        assertEquals("This profile has no custom skin", assertThrows(IOException.class,
            () -> LocalSkinSource.texture(profile("{\"textures\":{\"CAPE\":{\"url\":\"http://x/cape\"}}}"))).getMessage());
        // a profile with nothing in it, or a textures property with no textures, has no skin either (no crash)
        assertEquals("This profile has no custom skin", assertThrows(IOException.class, () -> LocalSkinSource.texture("{\"id\":\"x\"}")).getMessage());
        assertEquals("This profile has no custom skin", assertThrows(IOException.class, () -> LocalSkinSource.texture(profile("{}"))).getMessage());
        assertEquals("This profile has no custom skin", assertThrows(IOException.class, () -> LocalSkinSource.texture("{\"properties\":[{\"value\":\"x\"},{\"name\":\"textures\"}]}")).getMessage());
    }

    @Test void aFileIsReadAsItIsWithTheAskedModel(@TempDir Path folder) throws IOException {
        Path file = folder.resolve("skin.png");
        Files.write(file, png(64, 64));
        var skin = LocalSkinSource.resolve("  " + file + "  ", true);
        assertArrayEquals(png(64, 64), skin.png());
        assertTrue(skin.slim());
        assertTrue(assertThrows(IOException.class, () -> LocalSkinSource.resolve(folder.resolve("missing.png").toString(), false)).getMessage().startsWith("No file "));
        Files.write(folder.resolve("big.png"), new byte[LocalSkinSource.MAX_BYTES + 1]);
        assertEquals("Skin file exceeds 1 MB", assertThrows(IOException.class, () -> LocalSkinSource.resolve(folder.resolve("big.png").toString(), false)).getMessage());
        assertEquals("Enter a username, UUID, skin URL or the path of a PNG file", assertThrows(IOException.class, () -> LocalSkinSource.resolve("bad\0name", false)).getMessage());
    }

    /** A web server on this machine: a redirect is followed, an error status and an oversized body are refused. */
    @Test void aUrlIsFetchedThroughRedirectsAndWithinTheSizeLimit() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        byte[] skin = png(64, 64);
        server.createContext("/skin.png", exchange -> {
            exchange.sendResponseHeaders(200, skin.length);
            exchange.getResponseBody().write(skin);
            exchange.close();
        });
        server.createContext("/moved", exchange -> {
            exchange.getResponseHeaders().add("Location", "/skin.png");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/missing", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.createContext("/huge", exchange -> {
            exchange.sendResponseHeaders(200, LocalSkinSource.MAX_BYTES + 1L);
            exchange.getResponseBody().write(new byte[LocalSkinSource.MAX_BYTES + 1]);
            exchange.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            // Production fetch/resolve rejects plaintext HTTP and loopback/private addresses (SSRF protection)
            assertEquals("Use an HTTPS image URL", assertThrows(IOException.class, () -> LocalSkinSource.resolve(base + "/skin.png", false)).getMessage());
            assertEquals("Refusing connection to private or local address", assertThrows(IOException.class, () -> LocalSkinSource.fetch("https://127.0.0.1/skin.png")).getMessage());
            assertEquals("Refusing connection to private or local address", assertThrows(IOException.class, () -> LocalSkinSource.fetch("https://169.254.169.254/latest/meta-data")).getMessage());
            assertEquals("Refusing connection to private or local address", assertThrows(IOException.class, () -> LocalSkinSource.fetch("https://192.168.1.1/skin.png")).getMessage());
            assertEquals("https://textures.minecraft.net/texture/abc", LocalSkinSource.upgradeTextureUrl("http://textures.minecraft.net/texture/abc"));

            // HTTP transport, redirect following, status handling, and size limit via package-private test hook
            assertArrayEquals(skin, LocalSkinSource.fetch(base + "/skin.png", true));
            assertArrayEquals(skin, LocalSkinSource.fetch(base + "/moved", true));
            assertEquals("Server returned 404", assertThrows(IOException.class, () -> LocalSkinSource.fetch(base + "/missing", true)).getMessage());
            assertEquals("Response exceeds 1 MB", assertThrows(IOException.class, () -> LocalSkinSource.fetch(base + "/huge", true)).getMessage());
            assertEquals("Use an HTTPS image URL", assertThrows(IOException.class, () -> LocalSkinSource.fetch("file:///c:/skin.png")).getMessage());
            assertEquals("Use an HTTPS image URL", assertThrows(IOException.class, () -> LocalSkinSource.fetch("http://bad host/skin.png")).getMessage());
        } finally {
            server.stop(0);
        }
    }

    @Test void legacySkinIsAlwaysClassic() {
        assertFalse(LocalSkinSource.isSlim((x, y) -> 0, 64, 32));
        assertFalse(LocalSkinSource.isSlim(new int[64 * 32], 64, 32));
        BufferedImage img = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        assertFalse(LocalSkinSource.isSlim(img));
    }

    @Test void classicSkinDetectedWhenArm4thColumnIsOpaque() {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, 0xFF112233);
        img.setRGB(55, 20, 0xFF445566);
        assertFalse(LocalSkinSource.isSlim(img));

        int[] pixels = new int[64 * 64];
        pixels[20 * 64 + 55] = 0xFF445566;
        assertFalse(LocalSkinSource.isSlim(pixels, 64, 64));
        assertFalse(LocalSkinSource.isSlim((x, y) -> (x == 55 && y == 20) ? 0xFF445566 : 0, 64, 64));
    }

    @Test void slimSkinDetectedWhenAllArmCheckRegionsTransparent() {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 32; x++) {
            for (int y = 0; y < 32; y++) {
                img.setRGB(x, y, 0xFF888888);
            }
        }
        img.setRGB(53, 20, 0xFF123456);
        assertTrue(LocalSkinSource.isSlim(img));

        int[] pixels = new int[64 * 64];
        pixels[20 * 64 + 53] = 0xFF123456;
        assertTrue(LocalSkinSource.isSlim(pixels, 64, 64));
    }

    @Test void classicSkinWithFewTransparentArmPixelsIsStillClassic() {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(55, 36, 0xFFFFFFFF);
        assertFalse(LocalSkinSource.isSlim(img));
    }

    @Test void hdSkinScale2Detection() {
        BufferedImage classicHd = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        classicHd.setRGB(55 * 2, 20 * 2, 0xFFFFFFFF);
        assertFalse(LocalSkinSource.isSlim(classicHd));

        BufferedImage slimHd = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        slimHd.setRGB(53 * 2, 20 * 2, 0xFFFFFFFF);
        assertTrue(LocalSkinSource.isSlim(slimHd));
    }

    @Test void detectModelFromPngBytes() throws IOException {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(55, 20, 0xFFFFFFFF);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        byte[] classicBytes = baos.toByteArray();
        assertFalse(LocalSkinSource.detectModel(classicBytes, true));

        BufferedImage slimImg = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        slimImg.setRGB(53, 20, 0xFFFFFFFF);
        baos.reset();
        ImageIO.write(slimImg, "png", baos);
        byte[] slimBytes = baos.toByteArray();
        assertTrue(LocalSkinSource.detectModel(slimBytes, false));

        assertTrue(LocalSkinSource.detectModel(new byte[10], true));
        assertFalse(LocalSkinSource.detectModel(new byte[10], false));
    }

    @Test void eachOfEightArmRegionsFlagsClassic() {
        int[][] samples = {
            {50, 16},
            {54, 20},
            {50, 32},
            {54, 36},
            {42, 48},
            {46, 52},
            {58, 48},
            {62, 52}
        };
        for (int[] pt : samples) {
            int x = pt[0], y = pt[1];
            assertFalse(LocalSkinSource.isSlim((px, py) -> (px == x && py == y) ? 0xFFFFFFFF : 0, 64, 64),
                "Expected point (" + x + ',' + y + ") to flag classic model");
        }
    }
}
