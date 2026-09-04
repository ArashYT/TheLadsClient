package com.thelads.core;

import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.hud.HudElement;
import com.thelads.core.client.hud.HudManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class LadsGraphicsTest {
    static class MockGraphics implements LadsGraphics {
        final List<String> drawCalls = new ArrayList<>();
        int width = 800;
        int height = 600;

        @Override
        public void fill(int minX, int minY, int maxX, int maxY, int color) {
            drawCalls.add("fill:" + minX + "," + minY + "," + maxX + "," + maxY);
        }

        @Override
        public void drawText(String text, int x, int y, int color, boolean shadow) {
            drawCalls.add("text:" + text + "@" + x + "," + y);
        }

        @Override
        public void drawCenteredText(String text, int centerX, int y, int color, boolean shadow) {
            drawCalls.add("centerText:" + text + "@" + centerX + "," + y);
        }

        @Override
        public int textWidth(String text) {
            return text != null ? text.length() * 6 : 0;
        }

        @Override
        public int fontHeight() {
            return 9;
        }

        @Override
        public void pushPose() {
            drawCalls.add("pushPose");
        }

        @Override
        public void popPose() {
            drawCalls.add("popPose");
        }

        @Override
        public void translate(float x, float y) {
            drawCalls.add("translate:" + x + "," + y);
        }

        @Override
        public void scale(float sx, float sy) {
            drawCalls.add("scale:" + sx + "," + sy);
        }

        @Override
        public void enableScissor(int minX, int minY, int maxX, int maxY) {
            drawCalls.add("scissor:" + minX + "," + minY + "," + maxX + "," + maxY);
        }

        @Override
        public void disableScissor() {
            drawCalls.add("disableScissor");
        }

        @Override
        public void blit(String texture, int x, int y, int u, int v, int width, int height) {
            drawCalls.add("blit:" + texture);
        }

        @Override
        public void drawHead(String username, String uuid, int x, int y, int size) {
            drawCalls.add("head:" + username);
        }

        @Override
        public int getScaledWidth() {
            return width;
        }

        @Override
        public int getScaledHeight() {
            return height;
        }
    }

    @BeforeEach
    public void setup() {
        LadsGameBridge.set(new DefaultGameBridge());
    }

    @Test
    public void testHudManagerRendering() {
        HudManager manager = HudManager.getInstance();
        assertNotNull(manager);
        assertFalse(manager.getElements().isEmpty(), "HudManager should have 20 elements");

        MockGraphics g = new MockGraphics();
        for (HudElement el : manager.getElements()) {
            if (el.getModuleName() != null) {
                var m = com.thelads.core.config.ModuleManager.getInstance().getModule(el.getModuleName());
                if (m != null) m.setEnabled(true);
            }
            el.setEnabled(true);
        }

        manager.render(g);
        assertFalse(g.drawCalls.isEmpty(), "HudManager should issue draw calls to LadsGraphics");
    }
}
