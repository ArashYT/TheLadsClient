package com.thelads.core;

import com.thelads.core.client.title.TitleScreenTheme;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TitleScreenThemeTest {
    @Test void nativeActionsRemainOnscreenAndNeverOverlapAcrossGuiScales() {
        for (int[] size : new int[][] {{320, 180}, {320, 240}, {480, 270}, {530, 300}, {640, 360}, {960, 540}, {1920, 1080}}) {
          for (int count : new int[] {2, 6, 9, 10, 12, 16}) {
            var layout = TitleScreenTheme.layout(size[0], size[1], count);
            assertEquals(count, layout.buttons().size());
            for (int i = 0; i < layout.buttons().size(); i++) {
                var a = layout.buttons().get(i);
                assertTrue(a.width() > 0 && a.height() >= 14);
                assertTrue(a.x() >= 0 && a.y() >= 0);
                assertTrue(a.x() + a.width() <= size[0]);
                assertTrue(a.y() + a.height() <= size[1] - 29, "Button " + i + " of " + count + " must remain above account footer at " + size[0] + "x" + size[1]);
                for (int j = i + 1; j < layout.buttons().size(); j++) {
                    var b = layout.buttons().get(j);
                    assertTrue(a.x() + a.width() <= b.x() || b.x() + b.width() <= a.x() || a.y() + a.height() <= b.y() || b.y() + b.height() <= a.y());
                }
            }
          }
        }
    }

    @Test void optionalNativeControlsAreRetainedInLayout() {
        assertEquals(0, TitleScreenTheme.layout(640, 360, 0).buttons().size());
        assertEquals(12, TitleScreenTheme.layout(640, 360, 12).buttons().size());
        assertEquals(16, TitleScreenTheme.layout(960, 540, 16).buttons().size());
    }

    @Test void renderingUsesNoNetworkOrSkinLookups() {
        var g = new LadsGraphicsTest.MockGraphics();
        var layout = TitleScreenTheme.layout(640, 360, 9);
        TitleScreenTheme.renderBackground(g, layout, "LadsQA", "26.2", false, 1);
        var button = layout.buttons().getFirst();
        TitleScreenTheme.renderButton(g, button.x(), button.y(), button.width(), button.height(), "Singleplayer", "play", true, true, true, true, 1);
        assertFalse(g.drawCalls.stream().anyMatch(c -> c.startsWith("head:") || c.startsWith("blit:")));
        assertEquals(g.drawCalls.stream().filter(c -> c.equals("pushPose")).count(), g.drawCalls.stream().filter(c -> c.equals("popPose")).count());
        assertTrue(g.drawCalls.stream().anyMatch(c -> c.contains("LadsQA")));
    }

    @Test void backgroundWorkStaysBoundedAt4k() {
        var g = new LadsGraphicsTest.MockGraphics();
        TitleScreenTheme.renderBackground(g, TitleScreenTheme.layout(3840, 2160, 9), "LadsQA", "26.2", false, 1);
        assertTrue(g.drawCalls.size() < 1100, "Large windows must not create thousands of background fills");
    }
}
