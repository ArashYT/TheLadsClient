package com.thelads.smp.features.arise.nb2;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Shows remaining trade uses directly on villager trading interface buttons (Trade Uses).
 */
public final class TradeUsesManager {
    private TradeUsesManager() {}

    public static boolean isTradeUsesEnabled() {
        return NB2Config.get().tradeUses;
    }

    public static int getRemainingUses(int uses, int maxUses) {
        return Math.max(0, maxUses - uses);
    }

    public static String formatBadge(int uses, int maxUses) {
        return getRemainingUses(uses, maxUses) + "/" + maxUses;
    }

    public static int getBadgeColor(int uses, int maxUses) {
        int rem = getRemainingUses(uses, maxUses);
        if (rem <= 0) {
            return 0xFFFF5555; // Red
        } else if (rem <= 2) {
            return 0xFFFFAA00; // Yellow / Gold
        } else {
            return 0xFF55FF55; // Green
        }
    }

    @Environment(EnvType.CLIENT)
    public static void renderTradeBadge(GuiGraphicsExtractor graphics, Font font, int uses, int maxUses, int x, int y) {
        if (!isTradeUsesEnabled()) {
            return;
        }

        String badge = formatBadge(uses, maxUses);
        int color = getBadgeColor(uses, maxUses);

        // Position text centered between offer slots
        graphics.pose().pushMatrix();
        try {
            graphics.pose().scale(0.75f, 0.75f);
            int sx = (int) ((x + 22) / 0.75f);
            int sy = (int) ((y + 11) / 0.75f);
            graphics.text(font, badge, sx, sy, color, true);
        } finally {
            graphics.pose().popMatrix();
        }
    }
}
