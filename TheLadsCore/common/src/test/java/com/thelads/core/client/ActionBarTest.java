package com.thelads.core.client;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.hud.ActionBar;
import com.thelads.core.client.killbanner.KillBannerStyle;
import com.thelads.core.client.killbanner.KillBanners;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The action bar's state (ActionBar) and the kill banner rising above it (KillBanners.lift). */
class ActionBarTest {
    private final List<String> texts = new ArrayList<>();

    /** A GUI of this size, 6 px a character. */
    private LadsGraphics gui(int width, int height) {
        return new LadsGraphics() {
            @Override public void fill(int minX, int minY, int maxX, int maxY, int color) {}
            @Override public void drawText(String text, int x, int y, int color, boolean shadow) { texts.add(text); }
            @Override public void drawCenteredText(String text, int centerX, int y, int color, boolean shadow) {}
            @Override public int textWidth(String text) { return text.length() * 6; }
            @Override public int fontHeight() { return 9; }
            @Override public void pushPose() {}
            @Override public void popPose() {}
            @Override public void translate(float x, float y) {}
            @Override public void scale(float sx, float sy) {}
            @Override public void enableScissor(int minX, int minY, int maxX, int maxY) {}
            @Override public void disableScissor() {}
            @Override public void blit(String texture, int x, int y, int u, int v, int width, int height) {}
            @Override public void drawHead(String username, String uuid, int x, int y, int size) {}
            @Override public int getScaledWidth() { return width; }
            @Override public int getScaledHeight() { return height; }
        };
    }

    private static Module module() { return ModuleManager.getInstance().getModule(ActionBar.MODULE); }

    @AfterEach
    void reset() {
        module().setEnabled(false);
        ActionBar.clear();
        KillBanners.TIMELINE.clear();
    }

    /** The banner's lowest point (its HEADSHOT label's bottom) at 100%, as KillBanners.lift places it. */
    private static float bottom(LadsGraphics g, KillBannerStyle s) {
        float k = g.getScaledHeight() * KillBannerStyle.SCREEN_SCALE / 1080f * KillBannerStyle.ART_SCALE;
        return g.getScaledHeight() * .794f + (s.labelY + s.ring * 1.45f * 27 / 105f) * k;
    }

    @Test
    void aBannerStartingOverAnActionBarPlaysAboveItUntilItEndsAndTheNextOneDecidesAgain() {
        LadsGraphics g = gui(480, 270); // 1080p at GUI scale 4: vanilla's action bar is under the banner
        String message = "§6Hypixel §c❤ 100/100";
        assertFalse(ActionBar.set(message, false), "the module is off: vanilla shows it");
        KillBanners.TIMELINE.trigger(1, 0, false);
        int[] bar = ActionBar.spot(g);
        int width = g.actionBarWidth(message);
        assertArrayEquals(new int[] {240 - width / 2, 270 - 72, 240 - width / 2 + width, 270 - 63}, bar, "vanilla's place, 68 px above the bottom");
        float lift = KillBanners.lift(g, KillBannerStyle.REAVER, 1);
        assertTrue(lift > 0, "raised");
        assertTrue(bottom(g, KillBannerStyle.REAVER) - lift <= bar[1] - 2, "its bottom just above the action bar");
        assertTrue(bottom(g, KillBannerStyle.REAVER) - lift >= bar[1] - 4, "only as high as it needs");

        for (int i = 0; i < 60; i++) ActionBar.tick();
        assertNull(ActionBar.spot(g), "the action bar faded out");
        assertEquals(lift, KillBanners.lift(g, KillBannerStyle.REAVER, 1), "the banner keeps its place to its end");

        KillBanners.TIMELINE.trigger(2, 1, false);
        assertEquals(0, KillBanners.lift(g, KillBannerStyle.REAVER, 1), "the next banner, with no action bar: the normal place");
    }

    @Test
    void anActionBarClearOfTheBannerLeavesItWhereItIs() {
        ActionBar.set("Hypixel", false);
        KillBanners.TIMELINE.trigger(1, 0, false);
        assertEquals(0, KillBanners.lift(gui(1280, 720), KillBannerStyle.REAVER, 1), "1440p at GUI scale 2: the banner ends above it");
    }

    @Test
    void theModuleShowsItWhereItWasLaidOutForItsDurationOrNotAtAll() {
        module().setEnabled(true);
        assertTrue(ActionBar.set("Hypixel", false), "the module shows it: vanilla must not");
        for (int i = 0; i < 59; i++) ActionBar.tick();
        assertNotNull(ActionBar.text(), "3 s by default");
        assertEquals(255 / 20, ActionBar.alpha(ActionBar.left(0)), "fading out in its last second");
        assertEquals(255, ActionBar.alpha(ActionBar.left(0) + 20), "whole before it");
        ActionBar.tick();
        assertNull(ActionBar.text());

        ActionBar.set("Hypixel", true);
        assertNull(ActionBar.spot(gui(480, 270)), "not laid out yet");
        module().getOption("Hide Action Bar").load(com.google.gson.JsonParser.parseString("true"));
        assertNull(ActionBar.spot(gui(480, 270)), "hidden");
        module().getOption("Hide Action Bar").reset();
        assertNotEquals(0xFFFFFF, ActionBar.rgb(10), "a record's cycling colour, not white");
    }

    @Test
    void itsOwnColoursCanBeLeftOutKeepingTheRestOfItsFormatting() {
        LadsGraphics g = gui(480, 270);
        g.drawActionBar("§l§cBold red §7grey §kx", 0, 0, -1, true, true);
        g.drawActionBar("§cRed", 0, 0, -1, true, false);
        assertEquals(List.of("§l§rBold red §rgrey §kx", "§cRed"), texts, "a colour code also ends bold: a reset does too");
        assertEquals(30, g.actionBarWidth("Hello"));
    }
}
