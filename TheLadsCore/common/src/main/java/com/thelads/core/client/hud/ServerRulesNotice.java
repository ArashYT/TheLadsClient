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
 * Also renders the LadsClientAPI server policy toast (one toast per policy) triggered by LadsApiUi.
 */
public final class ServerRulesNotice {
    private static final long SHOW_NANOS = 8_000_000_000L;
    private static String seen = "";
    private static String text;
    private static long until;

    private static String policyTitle;
    private static String policyNotice;
    private static long policyUntil;

    private ServerRulesNotice() {}

    public static void showPolicyToast(String title, String notice) {
        policyTitle = title;
        policyNotice = notice;
        policyUntil = System.nanoTime() + SHOW_NANOS;
    }

    public static void clearPolicyToast() {
        policyTitle = null;
        policyNotice = null;
        policyUntil = 0;
    }

    /** Every frame: an address compare, then nothing unless a notice is showing. */
    static void render(LadsGraphics g) {
        LadsGameBridge game = g.getGame();
        if (game == null || !game.isIngame()) return;

        // 1. LadsClientAPI policy toast (higher priority)
        if (policyTitle != null) {
            if (System.nanoTime() - policyUntil > 0) {
                clearPolicyToast();
            } else {
                renderToast(g, policyTitle, policyNotice);
                return;
            }
        }

        // 2. Existing server rules notice
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

    private static void renderToast(LadsGraphics g, String title, String notice) {
        int tw = g.textWidth(title);
        int nw = (notice != null && !notice.isEmpty()) ? g.textWidth(notice) : 0;
        int w = Math.max(tw, nw) + 16;
        int x = Math.max(2, (g.getScaledWidth() - w) / 2);
        boolean twoLines = notice != null && !notice.isEmpty();
        int h = twoLines ? 28 : 16;
        int y = 4;
        // Background card matching client notification style
        g.fill(x, y, x + w, y + h, 0xE0130B10);
        // Top accent line
        g.fill(x, y, x + w, y + 2, 0xFFFF3B3B);
        // Toast texts
        g.drawText(title, x + 8, y + 5, 0xFFFFFFFF);
        if (twoLines) {
            g.drawText(notice, x + 8, y + 16, 0xFFCCCCCC);
        }
    }

    static String blockedText() {
        List<String> names = new ArrayList<>();
        // A LadsClientAPI block (Block.SERVER) is in the policy toast already and cannot be bypassed: not listed here.
        for (Module m : ModuleManager.getInstance().getModules())
            if (m.isEnabledSetting() && m.isBlockedByServer()
                    && com.thelads.core.client.HypixelSafetyManager.blockOf(m.getName()) != com.thelads.core.client.HypixelSafetyManager.Block.SERVER)
                names.add(m.getName());
        return names.isEmpty() ? null : String.join(", ", names) + " off on this server (server rules). Lads Settings > Bypass, at your own risk.";
    }
}
