package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.HudSettings;

import java.util.HashMap;
import java.util.Map;

/**
 * Lunar & Feather parity Tier Tagger HUD element.
 * Displays competitive PvP tier rankings (HT1, LT1, HT2, LT2, etc.) above player nametags and on HUD.
 * GATED: External ranking data advantage.
 */
public class TierTaggerHudElement extends HudElement {
    public static final String MODULE_NAME = "TierTagger";

    private static final Map<String, String> cachedTiers = new HashMap<>();

    static {
        // Built-in offline tier seeds
        cachedTiers.put("Minemen", "HT1");
        cachedTiers.put("TheLads", "HT1");
    }

    public static synchronized void setPlayerTier(String username, String tier) {
        if (username != null && tier != null) cachedTiers.put(username.toLowerCase(java.util.Locale.ROOT), tier);
    }

    public static synchronized String getPlayerTier(String username) {
        if (username == null) return "LT3";
        return cachedTiers.getOrDefault(username.toLowerCase(java.util.Locale.ROOT), "LT3");
    }

    public static String formatNametag(String username, String originalNametag) {
        String tier = getPlayerTier(username);
        return "[" + tier + "] " + originalNametag;
    }

    public TierTaggerHudElement() {
        useOrganizedDefaults();
    }

    @Override
    public void prepareRender(LadsGraphics graphics, boolean editor) {
        width = 105;
        height = 18;
    }

    @Override
    public void render(LadsGraphics graphics) {
        if (graphics == null) return;
        LadsGameBridge bridge = LadsGameBridge.get();
        LadsGameBridge.EntityTargetInfo target = bridge != null ? bridge.getTargetEntityInfo() : null;

        String name = (target != null && target.isPlayer()) ? target.name() : (bridge != null ? bridge.getPlayerName() : "Player");
        if (name == null || name.isEmpty()) name = "Player";
        String tier = getPlayerTier(name);

        drawBackground(graphics);

        boolean shadow = HudSettings.getInstance().isTextShadow();
        int textCol = resolveColor();
        String display = "[" + tier + "] " + name;
        int tw = graphics.textWidth(display);
        if (tw + 8 > width) width = tw + 8;
        graphics.drawCenteredText(display, x + width / 2, y + 5, textCol, shadow);
    }
}
