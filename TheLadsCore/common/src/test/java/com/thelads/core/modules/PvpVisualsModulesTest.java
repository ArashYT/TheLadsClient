package com.thelads.core.modules;

import static org.junit.jupiter.api.Assertions.*;

import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.client.PvpVisuals;
import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.ModuleManager;
import org.junit.jupiter.api.Test;

class PvpVisualsModulesTest {
    @Test void hitColorBecomesTheOverlayTexturesHurtPixel() {
        // The shader keeps the model's colour by the pixel's alpha: 1.20.1's own pixel is 0xB20000FF (ABGR), a 30% red flash.
        assertEquals(0xB2FF0000, HitColorModule.overlayPixel(HitColorModule.VANILLA));
        assertEquals(0xB20000FF, HitColorModule.abgr(HitColorModule.overlayPixel(HitColorModule.VANILLA)));
        assertEquals(0xFF00FF00, HitColorModule.overlayPixel(0x0000FF00), "no strength: the model untouched");
        assertEquals(0x0012_3456, HitColorModule.overlayPixel(0xFF123456), "full strength: the colour alone");
        assertEquals(0x80563412, HitColorModule.abgr(0x80123456));
    }

    @Test void glintDefaultIsMinecraftsOwnOnEveryVersion() {
        int vanilla = GlintColorizerModule.VANILLA;
        assertEquals(-8372020, GlintColorizerModule.vertexColor(vanilla), "1.8.9's renderEffect colour");
        for (int shift : new int[] {16, 8, 0}) assertEquals(1, GlintColorizerModule.modulator(vanilla, shift), 1e-6);
        // Red: modern multiplies only the red channel (by 255/128), 1.8.9 draws pure red.
        assertEquals(0xFFFF0000, GlintColorizerModule.vertexColor(0xFFFF0000));
        assertEquals(255F / 0x80, GlintColorizerModule.modulator(0xFFFF0000, 16), 1e-6);
        assertEquals(0, GlintColorizerModule.modulator(0xFFFF0000, 8));
        // Alpha is the brightness.
        assertEquals(0xFF402066, GlintColorizerModule.vertexColor(0x808040CC));
        assertEquals(128F / 255, GlintColorizerModule.modulator(0x808040CC, 0), 1e-6);
    }

    @Test void damageTintGrowsAsHealthDrops() {
        assertEquals(0, DamageTintModule.intensity(8, 8), "at the threshold: nothing");
        assertEquals(0, DamageTintModule.intensity(20, 8));
        assertEquals(0, DamageTintModule.intensity(0, 8), "dead: the death screen, no tint");
        assertEquals(DamageTintModule.START, DamageTintModule.intensity(7.999F, 8), 1e-3, "just under it: visible at once");
        assertEquals(1, DamageTintModule.intensity(0.0001F, 8), 1e-3);
        assertTrue(DamageTintModule.intensity(2, 8) > DamageTintModule.intensity(6, 8));
        assertEquals(1100, DamageTintModule.beatMillis(DamageTintModule.START));
        assertEquals(500, DamageTintModule.beatMillis(1));
    }

    @Test void damageTintBandsFadeInwards() {
        int argb = 0x99FF0000;
        assertEquals(0x99FF0000, DamageTintModule.bandColor(argb, 1, 0), "the outermost band: the colour itself");
        assertEquals(0xFF0000, DamageTintModule.bandColor(argb, 1, DamageTintModule.BANDS) & 0xFFFFFF);
        assertEquals(0, DamageTintModule.bandColor(argb, 1, DamageTintModule.BANDS) >>> 24, "fades out towards the middle");
        assertTrue(DamageTintModule.bandColor(argb, .5F, 0) >>> 24 < 0x99);
    }

    @Test void damageTintCornersHaveNoSeam() {
        for (int[] size : new int[][] {{270, 480}, {480, 270}, {320, 240}}) { // 9:16, 16:9, 4:3 (GUI pixels)
            int w = size[0], h = size[1], depth = Math.min(w, h) / 4;
            float[] a = new float[w * h];
            // Blend each fill over the pixels it covers, as the GPU does.
            LadsGraphics g = (LadsGraphics) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {LadsGraphics.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("fill")) {
                        float alpha = ((int) args[4] >>> 24) / 255F;
                        for (int y = (int) args[1]; y < (int) args[3]; y++)
                            for (int x = (int) args[0]; x < (int) args[2]; x++) a[y * w + x] += alpha * (1 - a[y * w + x]);
                    }
                    return null;
                });
            DamageTintModule.edges(g, w, h, 0x99FF0000, 1);
            assertTrue(a[0] > 0.5F, "the corner glows");
            assertEquals(0, a[h / 2 * w + w / 2], "the middle stays clear");
            for (int y = 0; y < depth; y++)
                for (int x = 0; x < depth; x++) {
                    // Every corner the same either side of its diagonal, and no hard step anywhere near it.
                    assertEquals(a[y * w + x], a[x * w + y], 1e-6, w + "x" + h + " top left " + x + "," + y);
                    assertEquals(a[y * w + x], a[(h - 1 - y) * w + (w - 1 - x)], 1e-6, w + "x" + h + " bottom right " + x + "," + y);
                    if (x > 0) assertTrue(Math.abs(a[y * w + x] - a[y * w + x - 1]) < 0.04F, w + "x" + h + " step at " + x + "," + y);
                }
        }
    }

    @Test void heartbeatIsTwoThumpsAndSilence() {
        int rate = 22050;
        byte[] pcm = DamageTintModule.heartbeat(rate);
        assertEquals(rate * 450 / 1000 * 2, pcm.length);
        assertTrue(peak(pcm, rate, 0, 0.1) > 8000, "lub");
        assertTrue(peak(pcm, rate, 0.2, 0.3) > 5000, "dub");
        assertTrue(peak(pcm, rate, 0.2, 0.3) < peak(pcm, rate, 0, 0.1), "dub is the softer");
        assertTrue(peak(pcm, rate, 0.39, 0.45) < 200, "silence after it");
    }

    @Test void throughWallsIsARiskyPartAndEveryModuleIsRegistered(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) {
        for (String name : PvpVisuals.MODULES) assertNotNull(ModuleManager.getInstance().getModule(name), name);
        HitboxesModule hitboxes = (HitboxesModule) ModuleManager.getInstance().getModule(HitboxesModule.NAME);
        String[] server = {"play.somesmp.net"};
        LadsGameBridge.set(new DefaultGameBridge() {
            @Override public String getServerAddress() { return server[0]; }
            @Override public boolean isIngame() { return true; }
        });
        HypixelSafetyManager.setBypassFile(tmp.resolve("server_bypasses.json").toFile());
        hitboxes.setEnabled(true);
        hitboxes.throughWalls.set(true);
        try {
            hitboxes.refresh();
            assertEquals(HypixelSafetyManager.Block.UNKNOWN, HypixelSafetyManager.blockOf(HitboxesModule.THROUGH_WALLS));
            assertFalse(hitboxes.walls, "unknown server rules: no boxes through walls");
            assertTrue(hitboxes.on, "the boxes themselves are F3+B: on everywhere");
            server[0] = "mc.hypixel.net";
            hitboxes.refresh();
            assertFalse(hitboxes.walls);
            assertTrue(hitboxes.on);
            server[0] = "Singleplayer";
            hitboxes.refresh();
            assertTrue(hitboxes.walls, "singleplayer: allowed");
        } finally {
            hitboxes.throughWalls.set(false);
            hitboxes.setEnabled(false);
            hitboxes.refresh();
            LadsGameBridge.set(new DefaultGameBridge());
            HypixelSafetyManager.setBypassFile(null);
        }
    }

    private static int peak(byte[] pcm, int rate, double from, double to) {
        int max = 0;
        for (int i = (int) (from * rate); i < (int) (to * rate); i++) max = Math.max(max, Math.abs((short) (pcm[2 * i] & 0xFF | pcm[2 * i + 1] << 8)));
        return max;
    }
}
