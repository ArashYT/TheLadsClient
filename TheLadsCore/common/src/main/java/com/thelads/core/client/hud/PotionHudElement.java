package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.HudSettings;
import java.util.List;
import java.util.regex.Pattern;

public class PotionHudElement extends HudElement {
    private LadsGraphics preparedGraphics;
    private List<String> effects = List.of();
    private static final Pattern DURATION_SUFFIX = Pattern.compile(" \\([0-9]+s\\)$");
    public PotionHudElement() {
        this.x = 10;
        this.y = 220;
        this.width = 90;
        this.height = 16;
    }

    @Override
    public void prepareRender(LadsGraphics g, boolean editor) {
        preparedGraphics = g;
        effects = List.copyOf(g.getGame().getActivePotionEffects());
        if (!optBool("Show duration", true)) {
            // Current native bridges append this suffix; preserve unfamiliar formats verbatim.
            effects = effects.stream().map(effect -> DURATION_SUFFIX.matcher(effect).replaceFirst("")).toList();
        }
        if (effects.isEmpty()) {
            this.width = optBool("Show when empty", false) ? Math.max(90, g.textWidth("No Effects") + 12) : 90;
            this.height = Math.max(16, g.fontHeight() + 6);
            return;
        }

        int lineH = g.fontHeight() + 3;
        this.height = effects.size() * lineH + 4;
        int maxW = 80;
        for (String eff : effects) {
            maxW = Math.max(maxW, g.textWidth(eff) + 8);
        }
        this.width = maxW;
    }

    @Override public void render(LadsGraphics g) {
        if (preparedGraphics != g) prepareRender(g, false);
        preparedGraphics = null;
        if (effects.isEmpty()) {
            if (optBool("Show when empty", false)) {
                drawBackground(g);
                drawCenteredText(g, "No Effects");
            }
            return;
        }
        int lineH = g.fontHeight() + 3;
        drawBackground(g);

        int ty = y + 2;
        int color = resolveColor();
        for (String eff : effects) {
            g.drawText(eff, x + 4, ty, color, HudSettings.getInstance().isTextShadow());
            ty += lineH;
        }
    }
}
