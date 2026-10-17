package com.thelads.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 1.9.10: the live game view in menu previews keeps 16:9 in any box (it was stretched to the box). */
class GameView16x9Test {
    private static final class Graphics extends LadsGraphicsTest.MockGraphics {
        int[] drawn;
        @Override public boolean drawGameView(int x, int y, int w, int h) { drawn = new int[] {x, y, w, h}; return true; }
    }

    @Test void theViewIsTheLargest16by9BoxCentredInThePreview() {
        var g = new Graphics();
        assertArrayEquals(new int[] {10, 101, 160, 90}, g.drawGameView16x9(10, 20, 160, 252), "a tall box: full width, centred");
        assertArrayEquals(g.drawn, g.drawGameView16x9(10, 20, 160, 252));
        assertArrayEquals(new int[] {60, 20, 160, 90}, g.drawGameView16x9(0, 20, 280, 90), "a wide box: full height, centred");
        assertNull(new LadsGraphicsTest.MockGraphics().drawGameView16x9(0, 0, 160, 90), "no game view: nothing drawn");
    }
}
