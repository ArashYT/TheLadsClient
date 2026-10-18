package com.thelads.core.client.hud;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.thelads.core.client.bridge.LadsGraphics;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The boxes a HUD cache composites (HudCache189, 1.9.13): poses applied, near draws merged, far ones apart, unknown sizes = all. */
class RecordingGraphicsBoundsTest {
    @Test
    void boxesFollowPosesMergeNearDrawsAndGiveUpOnUnknownSizes() {
        RecordingGraphics g = new RecordingGraphics(new Fake(), new ArrayList<>());
        g.fill(10, 10, 20, 20, -1);
        g.drawText("abc", 22, 10, -1, true); // 3 from the fill: one box; text is 3 * 6 + shadow wide, 9 + shadow high
        g.pushPose();
        g.translate(100, 50);
        g.scale(2, 2);
        g.fill(0, 0, 5, 5, -1); // far away, scaled: its own box
        g.popPose();
        g.fill(0, 0, 1, 1, -1); // back to the plain pose, 9 from the first box: apart
        List<float[]> boxes = g.bounds();
        assertEquals(3, boxes.size());
        assertArrayEquals(new float[]{10, 10, 41, 20}, boxes.get(0));
        assertArrayEquals(new float[]{100, 50, 110, 60}, boxes.get(1));
        assertArrayEquals(new float[]{0, 0, 1, 1}, boxes.get(2));
        g.fill(2, 2, 8, 8, -1); // bridges the corner box to the first: they merge
        assertEquals(2, g.bounds().size());
        g.drawBossBars(0, 0, 1, true, false); // this target cannot say where its boss bars go
        assertNull(g.bounds());
    }

    private static final class Fake implements LadsGraphics {
        @Override public void fill(int minX, int minY, int maxX, int maxY, int color) {}
        @Override public void drawText(String text, int x, int y, int color, boolean shadow) {}
        @Override public void drawCenteredText(String text, int centerX, int y, int color, boolean shadow) {}
        @Override public int textWidth(String text) { return text.length() * 6; }
        @Override public int fontHeight() { return 9; }
        @Override public void pushPose() {}
        @Override public void popPose() {}
        @Override public void translate(float x, float y) {}
        @Override public void scale(float sx, float sy) {}
        @Override public void enableScissor(int a, int b, int c, int d) {}
        @Override public void disableScissor() {}
        @Override public void blit(String texture, int x, int y, int u, int v, int w, int h) {}
        @Override public void drawHead(String username, String uuid, int x, int y, int size) {}
        @Override public int getScaledWidth() { return 960; }
        @Override public int getScaledHeight() { return 540; }
        @Override public com.thelads.core.client.bridge.LadsGameBridge getGame() { return null; }
    }
}
