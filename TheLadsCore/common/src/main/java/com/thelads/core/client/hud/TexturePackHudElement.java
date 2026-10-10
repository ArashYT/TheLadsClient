package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.HudSettings;
import java.util.List;

public class TexturePackHudElement extends HudElement {
    private LadsGraphics preparedGraphics;
    private List<String> names = List.of();
    private List<String> lines = List.of();
    /** Show Icons: each row starts with the pack's icon, a square as tall as the row less its gap (0 when off). */
    private int iconSize, iconGap;
    public TexturePackHudElement() {
        this.x = 10;
        this.y = 200;
        this.width = 110;
        this.height = 16;
    }

    @Override
    public void prepareRender(LadsGraphics g, boolean editor) {
        preparedGraphics = g;
        List<String> packs = g.getGame().getActiveResourcePacks();
        if (packs.isEmpty()) packs = List.of("Default");
        int limit = optBool("Show All", false) ? Math.max(1, Math.min(8, optCycle("Max Packs", 2) + 1)) : 1;
        int count = Math.min(packs.size(), limit);
        names = List.copyOf(packs.subList(0, count));
        lines = names.stream().map(pack -> "Pack: " + pack).toList();
        int lineHeight = g.fontHeight() + 3;
        boolean icons = optBool("Show Icons", true);
        iconSize = icons ? lineHeight - 1 : 0;
        iconGap = icons ? 4 : 0;
        this.width = 80;
        this.height = Math.max(16, count * lineHeight + 4);
        for (int i = 0; i < count; i++) {
            this.width = Math.max(width, iconSize + iconGap + g.textWidth(lines.get(i)) + 12);
        }
    }

    @Override public void render(LadsGraphics g) {
        if (preparedGraphics != g) prepareRender(g, false);
        preparedGraphics = null;
        drawBackground(g);
        int count = lines.size(), lineHeight = g.fontHeight() + 3;
        int color = resolveColor();
        boolean shadow = HudSettings.getInstance().isTextShadow();
        if (iconSize > 0) {
            // A lone pack is centred, icon and text together; a list is left aligned. Text sits level with the middle of its icon.
            for (int i = 0; i < count; i++) {
                int left = count == 1 ? x + (width - iconSize - iconGap - g.textWidth(lines.get(0))) / 2 : x + 6;
                int top = count == 1 ? y + (height - iconSize) / 2 : y + 2 + i * lineHeight;
                g.drawPackIcon(names.get(i), left, top, iconSize);
                g.drawText(lines.get(i), left + iconSize + iconGap, top + (iconSize - g.fontHeight()) / 2 + 1, color, shadow);
            }
        } else if (count == 1) {
            drawCenteredText(g, lines.get(0));
        } else {
            for (int i = 0; i < count; i++) {
                g.drawText(lines.get(i), x + 6, y + 2 + i * lineHeight, color, shadow);
            }
        }
    }
}
