package com.thelads.core;

import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.gui.DraggableHudScreen;
import com.thelads.core.client.gui.LadsSettingsScreen;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.config.SliderOption;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Pictures of the 1.7.2 menu changes drawn by the shared UI code itself (the code every version runs) onto an AWT image, at the
 * 640 x 360 GUI size of a 1280 x 720 window at GUI scale 2. Only with LADS_UI_SHOTS=&lt;folder&gt;; not game screenshots: AWT's font
 * stands in for Minecraft's and the preview of the live world is not drawn.
 */
class UiSnapshotsTest {
    @TempDir Path dir;

    private static final class Awt implements LadsGraphics {
        final BufferedImage image = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g = image.createGraphics();
        final FontMetrics metrics;
        final ArrayDeque<AffineTransform> poses = new ArrayDeque<>();
        Awt() {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 9));
            metrics = g.getFontMetrics();
            g.scale(2, 2);
            g.setClip(0, 0, 640, 360);
        }
        @Override public void fill(int x0, int y0, int x1, int y1, int color) { g.setColor(new Color(color, true)); g.fillRect(x0, y0, x1 - x0, y1 - y0); }
        @Override public void drawText(String text, int x, int y, int color, boolean shadow) {
            if (shadow) { g.setColor(new Color(0x80000000, true)); g.drawString(text, x + 1, y + metrics.getAscent() + 1); }
            g.setColor(new Color(color, true)); g.drawString(text, x, y + metrics.getAscent());
        }
        @Override public void drawCenteredText(String text, int cx, int y, int color, boolean shadow) { drawText(text, cx - textWidth(text) / 2, y, color, shadow); }
        @Override public int textWidth(String text) { return metrics.stringWidth(text); }
        @Override public int fontHeight() { return 9; }
        @Override public void pushPose() { poses.push(g.getTransform()); }
        @Override public void popPose() { g.setTransform(poses.pop()); }
        @Override public void translate(float x, float y) { g.translate(x, y); }
        @Override public void scale(float sx, float sy) { g.scale(sx, sy); }
        @Override public void enableScissor(int x0, int y0, int x1, int y1) { g.setClip(x0, y0, x1 - x0, y1 - y0); }
        @Override public void disableScissor() { g.setClip(0, 0, 640, 360); }
        @Override public void blit(String texture, int x, int y, int u, int v, int width, int height) { }
        @Override public void drawHead(String username, String uuid, int x, int y, int size) { }
        @Override public int getScaledWidth() { return 640; }
        @Override public int getScaledHeight() { return 360; }
        void save(String folder, String name) throws Exception { ImageIO.write(image, "png", new File(folder, name + ".png")); }
    }

    @Test void picturesOfTheResetQuestionsAndFullbrightsPage() throws Exception {
        String folder = System.getenv("LADS_UI_SHOTS");
        Assumptions.assumeTrue(folder != null && !folder.isBlank(), "LADS_UI_SHOTS is not set");
        new File(folder).mkdirs();
        ConfigManager.setTestConfigFile(dir.resolve("config.json").toFile());
        LadsGameBridge.set(new DefaultGameBridge());
        var ownership = new ModsMenuTest.OwnershipFixture();
        Map<String, int[]> positions = new HashMap<>();
        HudSettings.getInstance().getPositions().forEach((name, value) -> positions.put(name, value.clone()));
        Module cps = ModuleManager.getInstance().getModule("CPS"), fullbright = ModuleManager.getInstance().getModule("Fullbright");
        boolean cpsOn = cps.isEnabled(), fullbrightOn = fullbright.isEnabled();
        try {
            ModuleSupport.registerBuiltIn("CPS", "Fullbright", "Day", "FPS", "Health");
            for (String name : new String[] {"CPS", "Day", "FPS", "Health"}) ModuleManager.getInstance().getModule(name).setEnabled(true);
            HudSettings settings = HudSettings.getInstance();
            settings.getPositions().clear();
            settings.setPosition("CPS", 12, 42);
            settings.setPosition("Day", 12, 78);
            settings.setPosition("Health", 12, 112);
            settings.setPosition("FPS", 200, 20);

            var editor = new DraggableHudScreen(() -> { });
            Awt g = new Awt();
            editor.render(g, -1, -1);
            g.save(folder, "ui-hud-1-before");
            var reset = editor.controls().stream().filter(c -> c.id().equals("reset")).findFirst().orElseThrow().bounds();
            editor.mouseClicked(reset.x() + 3, reset.y() + 3, 0);
            g = new Awt();
            editor.render(g, -1, -1);
            g.save(folder, "ui-hud-2-dialog");
            var cancel = editor.confirmDialog().cancelBounds();
            editor.mouseClicked(cancel.x() + 3, cancel.y() + 3, 0);
            g = new Awt();
            editor.render(g, -1, -1);
            g.save(folder, "ui-hud-3-cancelled");
            assertNotNull(settings.getPosition("CPS"));
            editor.mouseClicked(reset.x() + 3, reset.y() + 3, 0);
            editor.keyPressed(257, 0);
            g = new Awt();
            editor.render(g, -1, -1);
            g.save(folder, "ui-hud-4-confirmed");
            assertNull(settings.getPosition("CPS"));

            ((SliderOption) cps.getOption("Size")).setValue(150);
            var menu = new LadsSettingsScreen();
            menu.openModule("CPS");
            menu.setReducedMotion(true);
            menu.render(new Awt(), -1, -1);
            menu.mouseScrolled(300, 200, -30); // Reset options sits below the last option
            g = new Awt();
            menu.render(g, -1, -1);
            g.save(folder, "ui-module-1-before");
            var button = menu.controlBounds("reset");
            menu.mouseClicked(button.x() + 3, button.y() + 3, 0);
            g = new Awt();
            menu.render(g, -1, -1);
            g.save(folder, "ui-module-2-dialog");
            var no = menu.confirmDialog().cancelBounds();
            menu.mouseClicked(no.x() + 3, no.y() + 3, 0);
            g = new Awt();
            menu.render(g, -1, -1);
            g.save(folder, "ui-module-3-cancelled");
            assertEquals(150, ((SliderOption) cps.getOption("Size")).getValue());
            menu.mouseClicked(button.x() + 3, button.y() + 3, 0);
            menu.keyPressed(257, 0);
            g = new Awt();
            menu.render(g, -1, -1);
            g.save(folder, "ui-module-4-confirmed");
            assertEquals(100, ((SliderOption) cps.getOption("Size")).getValue());

            var gamma = (SliderOption) fullbright.getOption("Gamma");
            var page = new LadsSettingsScreen();
            page.openModule("Fullbright");
            for (int percent : new int[] {30, 65, 100}) {
                gamma.setValue(percent);
                g = new Awt();
                page.render(g, -1, -1);
                g.save(folder, "ui-fb-slider-" + percent);
            }
            var field = page.controlBounds("option:Gamma:field");
            page.mouseClicked(field.x() + 3, field.y() + 3, 0);
            "40".codePoints().forEach(page::charTyped);
            g = new Awt();
            page.render(g, -1, -1);
            g.save(folder, "ui-fb-typing-40");
            page.keyPressed(257, 0);
            assertEquals(40, gamma.getValue());
            g = new Awt();
            page.render(g, -1, -1);
            g.save(folder, "ui-fb-typed-40");
        } finally {
            HudSettings.getInstance().getPositions().clear();
            HudSettings.getInstance().getPositions().putAll(positions);
            fullbright.getOptions().forEach(option -> option.reset());
            cps.getOptions().forEach(option -> option.reset());
            cps.setEnabled(cpsOn);
            fullbright.setEnabled(fullbrightOn);
            ownership.close();
            ConfigManager.setTestConfigFile(null);
        }
    }
}
