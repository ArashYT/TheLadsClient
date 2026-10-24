package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGameBridge.CooldownEntry;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.HudSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CooldownHudElement extends HudElement {
    public CooldownHudElement() {
        this.x = 10;
        this.y = 120;
        this.width = 100;
        this.height = 36;
    }

    @Override
    public void prepareRender(LadsGraphics g, boolean editor) {
        List<CooldownEntry> list = getEntries(g, editor);
        int count = Math.max(1, list.size());
        int mode = optCycle("Display mode", 0);
        int rowHeight = mode == 1 ? 16 : (g.fontHeight() + 3);
        width = 100;
        height = Math.max(16, count * rowHeight + 4);
    }

    @Override
    public void render(LadsGraphics g) {
        renderCooldowns(g, false);
    }

    @Override
    public void renderEditor(LadsGraphics g) {
        renderCooldowns(g, true);
    }

    private List<CooldownEntry> getEntries(LadsGraphics g, boolean editor) {
        if (editor) {
            return List.of(
                new CooldownEntry("Ender Pearl", 0.65f, 9.8f),
                new CooldownEntry("Shield", 0.35f, 1.8f)
            );
        }
        var active = g.getGame().getActiveCooldowns();
        boolean includePearls = optBool("Include pearls", true);
        if (!includePearls) {
            List<CooldownEntry> filtered = new ArrayList<>();
            for (CooldownEntry e : active) {
                if (!e.name().equalsIgnoreCase("Ender Pearl")) filtered.add(e);
            }
            return filtered;
        }
        return active;
    }

    private void renderCooldowns(LadsGraphics g, boolean editor) {
        List<CooldownEntry> list = getEntries(g, editor);
        if (list.isEmpty()) return;

        drawBackground(g);
        int mode = optCycle("Display mode", 0); // 0 = Text & Time, 1 = Bar & Time, 2 = Compact
        boolean shadow = HudSettings.getInstance().isTextShadow();
        int textColor = resolveColor();

        int yOff = y + 3;
        for (CooldownEntry entry : list) {
            String timeStr = String.format(Locale.ROOT, "%.1fs", entry.remainingSeconds());
            if (mode == 1) { // Bar & Time
                g.drawText(entry.name(), x + 4, yOff, textColor, shadow);
                int tw = g.textWidth(timeStr);
                g.drawText(timeStr, x + width - tw - 4, yOff, textColor, shadow);
                int barY = yOff + g.fontHeight() + 1;
                int barW = width - 8;
                g.fill(x + 4, barY, x + 4 + barW, barY + 3, 0x40FFFFFF);
                int fillW = (int) (barW * Math.max(0f, Math.min(1f, entry.percent())));
                g.fill(x + 4, barY, x + 4 + fillW, barY + 3, textColor);
                yOff += 16;
            } else if (mode == 2) { // Compact
                String line = entry.name().substring(0, Math.min(3, entry.name().length())) + ": " + timeStr;
                g.drawText(line, x + 4, yOff, textColor, shadow);
                yOff += g.fontHeight() + 3;
            } else { // Text & Time
                String line = entry.name() + ": " + timeStr;
                g.drawText(line, x + 4, yOff, textColor, shadow);
                yOff += g.fontHeight() + 3;
            }
        }
    }
}
