package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import java.util.ArrayList;
import java.util.List;

/**
 * Joining a server that turns modules the player has on off (its rules ban them, or we don't know its rules: HypixelSafetyManager)
 * says so at the top of the screen for a few seconds, once per join, so a minimap does not just vanish.
 */
final class ServerRulesNotice {
    private static final long SHOW_NANOS = 8_000_000_000L;
    private static String seen = "";
    private static String text;
    private static long until;

    private ServerRulesNotice() {}

    /** Every frame: an address compare, then nothing unless a notice is showing. */
    static void render(LadsGraphics g) {
        LadsGameBridge game = g.getGame();
        if (game == null || !game.isIngame()) return;
        String address = game.getServerAddress();
        if (address != seen && address != null && !address.equals(seen)) {
            seen = address;
            text = blockedText();
            until = text == null ? 0 : System.nanoTime() + SHOW_NANOS;
        }
        if (until == 0) return;
        if (System.nanoTime() - until > 0) { until = 0; return; }
        int w = g.textWidth(text) + 12, x = Math.max(2, (g.getScaledWidth() - w) / 2);
        g.fill(x, 4, x + w, 18, 0xC8500000);
        g.drawText(text, x + 6, 7, 0xFFFFE0E0);
    }

    static String blockedText() {
        List<String> names = new ArrayList<>();
        for (Module m : ModuleManager.getInstance().getModules())
            if (m.isEnabledSetting() && m.isBlockedByServer()) names.add(m.getName());
        return names.isEmpty() ? null : String.join(", ", names) + " off on this server (server rules). Lads Settings > Bypass, at your own risk.";
    }
}
