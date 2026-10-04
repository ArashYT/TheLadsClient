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

    /** The title's own controls (menu, fullscreen top right, Switch after the account name, Essential's row) never overlap. */
    @Test void titleControlsStayApartFromSmallWindowsToLargeOnes() {
        for (int w = 240; w <= 1000; w += 20)
          for (int h = 180; h <= 560; h += 20)
            for (int count : new int[] {6, 7, 8})
              for (boolean essential : new boolean[] {false, true}) {
                var layout = TitleScreenTheme.layout(w, h, count, essential);
                var g = new LadsGraphicsTest.MockGraphics();
                var switchBox = TitleScreenTheme.renderBackground(g, layout, "Sixteen_Letters1", "26.2", false, 0, true);
                String at = count + " buttons at " + w + "x" + h + (essential ? " with Essential" : "");
                int buildLeft = w - 18 - g.textWidth(LadsVersion.clientName() + " (26.2)");
                assertTrue(switchBox.x() >= 27 && switchBox.x() + switchBox.width() <= buildLeft, "Switch between the name and the build " + at);
                var boxes = new java.util.ArrayList<>(layout.buttons());
                boxes.add(new TitleScreenTheme.Rect(w - 26, 6, 20, 20));
                boxes.add(switchBox);
                if (essential) boxes.add(new TitleScreenTheme.Rect(16, h - 53, TitleScreenTheme.essentialRowWidth(layout), 18));
                for (int i = 0; i < boxes.size(); i++) {
                    var a = boxes.get(i);
                    assertTrue(a.x() >= 0 && a.y() >= 0 && a.x() + a.width() <= w && a.y() + a.height() <= h, "box " + i + " on screen, " + at);
                    for (int j = i + 1; j < boxes.size(); j++) {
                        var b = boxes.get(j);
                        assertTrue(a.x() + a.width() <= b.x() || b.x() + b.width() <= a.x() || a.y() + a.height() <= b.y() || b.y() + b.height() <= a.y(),
                            "boxes " + i + " and " + j + " overlap, " + at);
                    }
                }
              }
        // Roomy: Switch is labelled; cramped: its icon alone.
        var g = new LadsGraphicsTest.MockGraphics();
        assertEquals(g.textWidth("Switch") + TitleScreenTheme.COMPACT_PADDING,
            TitleScreenTheme.renderBackground(g, TitleScreenTheme.layout(640, 360, 8), "LadsQA", "26.2", false, 0, true).width());
        assertEquals(16, TitleScreenTheme.renderBackground(g, TitleScreenTheme.layout(240, 180, 8), "LadsQA", "26.2", false, 0, true).width());
        assertNull(TitleScreenTheme.renderBackground(g, TitleScreenTheme.layout(640, 360, 8), "LadsQA", "26.2", false, 0, false));
    }

    @Test void pauseIconsSitBeforeTheirCentredLabel() {
        int icon = TitleScreenTheme.LABEL_ICON + TitleScreenTheme.LABEL_ICON_GAP;
        int left = TitleScreenTheme.iconLabelX(100, 200, 60);
        assertEquals(left - icon - 100, 300 - (left + 60), "icon and label centred together: equal margins");
        assertEquals(100 + 4 + icon, TitleScreenTheme.iconLabelX(100, 40, 60), "a label too long starts after the icon at the edge");
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
