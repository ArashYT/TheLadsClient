package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.HudSettings;
import java.util.List;

public class TexturePackHudElement extends HudElement {
    private LadsGraphics preparedGraphics;
    private List<String> lines = List.of();
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
        lines = packs.subList(0, count).stream().map(pack -> "Pack: " + pack).toList();
        int lineHeight = g.fontHeight() + 3;
        this.width = 80;
        this.height = Math.max(16, count * lineHeight + 4);
        for (int i = 0; i < count; i++) {
            this.width = Math.max(width, g.textWidth(lines.get(i)) + 12);
        }
    }

    @Override public void render(LadsGraphics g) {
        if (preparedGraphics != g) prepareRender(g, false);
        preparedGraphics = null;
        drawBackground(g);
        int count = lines.size(), lineHeight = g.fontHeight() + 3;
        if (count == 1) {
            drawCenteredText(g, lines.get(0));
        } else {
            int color = resolveColor();
            for (int i = 0; i < count; i++) {
                g.drawText(lines.get(i), x + 6, y + 2 + i * lineHeight,
                        color, HudSettings.getInstance().isTextShadow());
            }
        }
    }
}
