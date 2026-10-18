package com.thelads.core;

import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.gui.LadsSettingsScreen;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.HudSettings;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The HUD frame rate cap popup: a modal layer in front of the screen (1.20.1 depth-tests its GUI, so the popup is drawn through
 * LadsGraphics.overlay and the screen under it must not highlight), every control inside its box and clear of the others at every
 * window size vanilla allows (GUI at least 320 x 240), and the controls act where they are drawn.
 */
class HudFpsPopupTest {
    @TempDir Path dir;
    private int savedLimit;
    private boolean savedEnabled;

    private static final class Screen extends LadsGraphicsTest.MockGraphics {
        record Text(String text, int x, int y, int depth) {}
        final List<Text> texts = new ArrayList<>();
        int depth, w = 640, h = 360, fills;
        @Override public void overlay(Runnable draw) { depth++; try { draw.run(); } finally { depth--; } }
        @Override public void drawText(String text, int x, int y, int color, boolean shadow) { texts.add(new Text(text, x, y, depth)); }
        @Override public void drawCenteredText(String text, int x, int y, int color, boolean shadow) { texts.add(new Text(text, x - textWidth(text) / 2, y, depth)); }
        @Override public void fill(int x0, int y0, int x1, int y1, int color) { fills++; }
        @Override public int getScaledWidth() { return w; }
        @Override public int getScaledHeight() { return h; }
        void frame(LadsSettingsScreen ui, int mx, int my) { texts.clear(); ui.render(this, mx, my); }
    }

    @BeforeEach void setUp() {
        ConfigManager.setTestConfigFile(dir.resolve("config.json").toFile());
        LadsGameBridge.set(new DefaultGameBridge());
        savedLimit = HudSettings.getInstance().getHudFpsLimit();
        savedEnabled = HudSettings.getInstance().isHudFpsCapEnabled();
    }
    @AfterEach void tearDown() {
        HudSettings.getInstance().setHudFpsLimit(savedLimit);
        HudSettings.getInstance().setHudFpsCapEnabled(savedEnabled);
        ConfigManager.setTestConfigFile(null);
    }

    @Test void popupIsAnOverlayLayerAndTheScreenUnderItIsInTheBackground() {
        var ui = new LadsSettingsScreen();
        var g = new Screen();
        g.frame(ui, -1, -1);
        assertTrue(g.texts.stream().noneMatch(t -> t.depth() > 0), "no popup, no layer");
        ui.openHudFpsDialog();
        g.frame(ui, -1, -1);
        assertTrue(g.texts.stream().anyMatch(t -> t.text().equals("GLOBAL HUD FRAME RATE CAP") && t.depth() == 1), "the popup's title is drawn in a layer");
        assertTrue(g.texts.stream().filter(t -> t.depth() == 0).anyMatch(t -> t.text().equals("THE LADS")), "the screen under it stays in the background");
        assertTrue(g.texts.stream().filter(t -> t.depth() == 0).noneMatch(t -> t.text().startsWith("Current HUD Rate")), "the popup's own text is never in the background");
    }

    @Test void everyControlIsInsideTheBoxAndClearOfTheOthersAtEveryWindowSize() {
        int[][] sizes = {{640, 360}, {472, 250}, {320, 240}, {800, 450}, {480, 270}, {400, 300}};
        for (int[] size : sizes) {
            var ui = new LadsSettingsScreen();
            var g = new Screen();
            g.w = size[0]; g.h = size[1];
            ui.openHudFpsDialog();
            g.frame(ui, -1, -1);
            var rects = ui.hudFpsDialogRects();
            assertFalse(rects.isEmpty(), "drawn at " + size[0] + "x" + size[1]);
            var box = rects.get(0);
            String at = " at " + size[0] + "x" + size[1];
            assertTrue(box.x() >= 0 && box.y() >= 0 && box.x() + box.width() <= size[0] && box.y() + box.height() <= size[1], "the box is on screen" + at + ": " + box);
            for (int i = 1; i < rects.size(); i++) {
                var r = rects.get(i);
                assertTrue(r.x() >= box.x() && r.y() >= box.y() && r.x() + r.width() <= box.x() + box.width() && r.y() + r.height() <= box.y() + box.height(),
                    "control " + i + " " + r + " is inside the box " + box + at);
                for (int j = i + 1; j < rects.size(); j++) {
                    var o = rects.get(j);
                    boolean apart = r.x() + r.width() <= o.x() || o.x() + o.width() <= r.x() || r.y() + r.height() <= o.y() || o.y() + o.height() <= r.y();
                    assertTrue(apart, "controls " + i + " " + r + " and " + j + " " + o + " overlap" + at);
                }
            }
            for (var t : g.texts) {
                if (t.depth() == 0) continue;
                assertTrue(t.x() >= box.x() && t.y() >= box.y() && t.y() + 9 <= box.y() + box.height(),
                    "'" + t.text() + "' at " + t.x() + "," + t.y() + " is inside the box " + box + at);
            }
        }
    }

    /** 1.9.11: HUD FPS, Backgrounds, Text Shadow, Colors and Done stay on screen, apart, and clear of "THE LADS" down to 320 GUI px. */
    @Test void headerButtonsStayOnScreenAndClearOfTheTitleAtEveryWindowSize() {
        HudSettings.getInstance().setHudFpsCapEnabled(true);
        HudSettings.getInstance().setHudFpsLimit(60); // "60 (Recommended)": the widest label
        String[] ids = {"global-hud-fps", "global-backgrounds", "global-text-shadow", "global-colors", "close"};
        for (int[] size : new int[][] {{320, 240}, {360, 240}, {427, 240}, {472, 250}, {640, 360}, {960, 540}}) {
            var ui = new LadsSettingsScreen();
            var g = new Screen();
            g.w = size[0]; g.h = size[1];
            g.frame(ui, -1, -1);
            String at = " at " + size[0] + "x" + size[1];
            var rects = new ArrayList<LadsSettingsScreen.Rect>();
            for (String id : ids) { var r = ui.controlBounds(id); assertNotNull(r, id + at); rects.add(r); }
            for (int i = 0; i < rects.size(); i++) {
                var r = rects.get(i);
                assertTrue(r.x() >= 0 && r.x() + r.width() <= size[0], ids[i] + " " + r + " is on screen" + at);
                for (int j = i + 1; j < rects.size(); j++) {
                    var o = rects.get(j);
                    assertTrue(r.x() + r.width() <= o.x() || o.x() + o.width() <= r.x(), ids[i] + " and " + ids[j] + " overlap" + at);
                }
                for (var t : g.texts) if (t.text().equals("THE LADS"))
                    assertTrue(t.x() + g.textWidth(t.text()) <= r.x(), "THE LADS runs into " + ids[i] + " " + r + at);
            }
        }
    }

    @Test void presetsAndTheToggleActWhereTheyAreDrawn() {
        var ui = new LadsSettingsScreen();
        var g = new Screen();
        g.w = 472; g.h = 250;
        HudSettings.getInstance().setHudFpsCapEnabled(false);
        ui.openHudFpsDialog();
        g.frame(ui, -1, -1);
        var rects = ui.hudFpsDialogRects();
        int[] levels = HudSettings.HUD_FPS_LEVELS;
        // box, close, toggle, input, apply, then the presets in order
        for (int i = 0; i < levels.length; i++) {
            var r = rects.get(5 + i);
            assertTrue(ui.mouseClicked(r.x() + r.width() / 2.0, r.y() + r.height() / 2.0, 0));
            ui.mouseReleased(r.x() + r.width() / 2.0, r.y() + r.height() / 2.0, 0);
            assertEquals(levels[i], HudSettings.getInstance().getHudFpsLimit(), "preset " + i);
            assertTrue(HudSettings.getInstance().isHudFpsCapEnabled());
            g.frame(ui, -1, -1);
        }
        var toggle = rects.get(2);
        ui.mouseClicked(toggle.x() + 3, toggle.y() + 3, 0);
        assertFalse(HudSettings.getInstance().isHudFpsCapEnabled(), "the toggle turns the cap off");
        var close = rects.get(1);
        ui.mouseClicked(close.x() + 3, close.y() + 3, 0);
        g.frame(ui, -1, -1);
        assertTrue(ui.hudFpsDialogRects().isEmpty(), "Done closes it");
    }

    @Test void thePopupIsModalForTheMouseAndTheWheel() {
        var ui = new LadsSettingsScreen();
        var g = new Screen();
        ui.openHudFpsDialog();
        g.frame(ui, -1, -1);
        assertTrue(ui.mouseScrolled(20, 120, -1), "the wheel does not reach the screen under the popup");
        var box = ui.hudFpsDialogRects().get(0);
        assertTrue(ui.mouseClicked(box.x() - 5, box.y() - 5, 0), "a click outside only closes it");
        g.frame(ui, -1, -1);
        assertTrue(ui.hudFpsDialogRects().isEmpty());
    }
}
