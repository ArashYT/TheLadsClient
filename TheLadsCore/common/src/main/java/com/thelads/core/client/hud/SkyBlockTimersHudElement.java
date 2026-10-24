package com.thelads.core.client.hud;

import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.hypixel.SkyBlockTimers;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;

/**
 * Hypixel SkyBlock Garden crop, pest, and composter countdown timer HUD.
 * Gated under HypixelSafetyManager.
 */
public class SkyBlockTimersHudElement extends HudElement {
    public static final String ID = "SkyBlockTimers";

    public SkyBlockTimersHudElement() {
        this.width = 110;
        this.height = 36;
        this.moduleName = ID;
        useOrganizedDefaults();
    }

    @Override
    public void render(LadsGraphics g) {
        if (!shouldRender(false)) return;
        drawTimers(g, false);
    }

    @Override
    public void renderEditor(LadsGraphics g) {
        drawTimers(g, true);
    }

    private boolean shouldRender(boolean editor) {
        if (editor) return true;
        Module m = ModuleManager.getInstance().getModule(ID);
        if (m == null || !m.isEnabled()) return false;
        if (HypixelSafetyManager.isDisallowed(ID) || HypixelSafetyManager.isDisallowed("SkyBlock Suite")) return false;
        return true;
    }

    private void drawTimers(LadsGraphics g, boolean preview) {
        int dx = getDisplayX(g);
        int dy = getDisplayY(g);
        int w = width;
        int h = height;

        int bg = resolveBackground();
        if (bg == 0) bg = 0xB0101016;
        g.fill(dx, dy, dx + w, dy + h, bg);

        String contest = SkyBlockTimers.getContestString(preview);
        String composter = SkyBlockTimers.getComposterString(preview);
        String pest = SkyBlockTimers.getPestString(preview);

        boolean shadow = HudSettings.getInstance().isTextShadow();
        g.drawText(contest, dx + 4, dy + 3, 0xFFFFCC00, shadow);
        g.drawText(composter, dx + 4, dy + 14, 0xFF55FF55, shadow);
        g.drawText(pest, dx + 4, dy + 25, 0xFFFF5555, shadow);
    }
}
