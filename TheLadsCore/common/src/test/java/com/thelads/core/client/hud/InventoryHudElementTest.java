package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.bridge.LadsGraphics;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InventoryHudElementTest {
    private static class FakeGraphics implements LadsGraphics {
        int slotsDrawn = 0, fills = 0;
        boolean emptyInventory;

        @Override public void fill(int minX, int minY, int maxX, int maxY, int color) { fills++; }
        @Override public void drawText(String text, int x, int y, int color, boolean shadow) {}
        @Override public void drawCenteredText(String text, int centerX, int y, int color, boolean shadow) {}
        @Override public int textWidth(String text) { return text != null ? text.length() * 6 : 0; }
        @Override public int fontHeight() { return 9; }
        @Override public void pushPose() {}
        @Override public void popPose() {}
        @Override public void translate(float x, float y) {}
        @Override public void scale(float sx, float sy) {}
        @Override public void enableScissor(int minX, int minY, int maxX, int maxY) {}
        @Override public void disableScissor() {}
        @Override public void blit(String texture, int x, int y, int u, int v, int width, int height) {}
        @Override public void drawHead(String username, String uuid, int x, int y, int size) {}
        @Override public LadsGameBridge getGame() {
            return new DefaultGameBridge() {
                @Override public boolean isMainInventoryEmpty() { return emptyInventory; }
            };
        }
        @Override public int getScaledWidth() { return 800; }
        @Override public int getScaledHeight() { return 600; }
        @Override public double hudUnit() { return 1.0; }
        @Override public Object textMetricsKey() { return "key"; }

        @Override
        public void drawInventorySlot(int slot, int x, int y, boolean preview) {
            slotsDrawn++;
        }
    }

    @Test
    void testDimensionsAndRendering() {
        InventoryHudElement hud = new InventoryHudElement();
        assertEquals(168, hud.width);
        assertEquals(60, hud.height);

        FakeGraphics g = new FakeGraphics();
        hud.render(g);
        assertEquals(27, g.slotsDrawn);

        FakeGraphics g2 = new FakeGraphics();
        hud.renderEditor(g2);
        assertEquals(27, g2.slotsDrawn);
    }

    @Test
    void anEmptyInventoryDrawsNothingButTheEditorShowsSamples() {
        InventoryHudElement hud = new InventoryHudElement();
        FakeGraphics g = new FakeGraphics();
        g.emptyInventory = true;
        hud.render(g);
        assertEquals(0, g.slotsDrawn);
        assertEquals(0, g.fills, "no empty panel");
        hud.renderEditor(g);
        assertEquals(27, g.slotsDrawn, "the editor's placeholder items");
    }
}
