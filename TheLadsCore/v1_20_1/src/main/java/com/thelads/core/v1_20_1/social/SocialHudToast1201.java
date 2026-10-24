package com.thelads.core.v1_20_1.social;

import com.thelads.core.client.gui.LadsPalette;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Renders Lads social toasts (invites, join requests, friend requests) directly on the HUD layer (top right).
 */
public final class SocialHudToast1201 {
    public static final class ToastItem {
        final String title;
        final String body;
        final String acceptAction;
        final String denyAction;
        final long createdAt;
        final long lifetimeMillis;

        public ToastItem(String title, String body, String acceptAction, String denyAction, long lifetimeMillis) {
            this.title = title;
            this.body = body;
            this.acceptAction = acceptAction;
            this.denyAction = denyAction;
            this.createdAt = System.currentTimeMillis();
            this.lifetimeMillis = lifetimeMillis;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - createdAt > lifetimeMillis;
        }
    }

    private static final List<ToastItem> ACTIVE_TOASTS = new ArrayList<>();
    private static final int TOAST_WIDTH = 190;
    private static final int TOAST_HEIGHT = 44;
    private static final int TOAST_MARGIN = 6;
    private static final long DEFAULT_LIFETIME = 15_000L; // 15 seconds

    private SocialHudToast1201() {}

    public static synchronized void addToast(String title, String body, String acceptAction, String denyAction) {
        ACTIVE_TOASTS.add(0, new ToastItem(title, body, acceptAction, denyAction, DEFAULT_LIFETIME));
        if (ACTIVE_TOASTS.size() > 5) {
            ACTIVE_TOASTS.remove(ACTIVE_TOASTS.size() - 1);
        }
    }

    public static synchronized boolean acceptNewest() {
        if (!ACTIVE_TOASTS.isEmpty()) {
            ToastItem toast = ACTIVE_TOASTS.remove(0);
            if (toast.acceptAction != null) {
                SocialBridge1201.handleChatAction(toast.acceptAction);
                return true;
            }
        }
        return false;
    }

    public static synchronized boolean denyNewest() {
        if (!ACTIVE_TOASTS.isEmpty()) {
            ToastItem toast = ACTIVE_TOASTS.remove(0);
            if (toast.denyAction != null) {
                SocialBridge1201.handleChatAction(toast.denyAction);
                return true;
            }
        }
        return false;
    }

    public static synchronized void render(GuiGraphics g, float tickDelta) {
        if (ACTIVE_TOASTS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null && mc.options.hideGui) return;

        Font font = mc.font;
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int y = TOAST_MARGIN;

        Iterator<ToastItem> it = ACTIVE_TOASTS.iterator();
        while (it.hasNext()) {
            ToastItem toast = it.next();
            if (toast.isExpired()) {
                it.remove();
                continue;
            }

            int x = screenWidth - TOAST_WIDTH - TOAST_MARGIN;
            int height = TOAST_HEIGHT;

            // Background card with Lads border
            g.fill(x, y, x + TOAST_WIDTH, y + height, 0xEE141014);
            g.fill(x, y, x + 2, y + height, 0xFFCF1535); // Left accent bar
            g.fill(x, y, x + TOAST_WIDTH, y + 1, 0x44FFFFFF);
            g.fill(x, y + height - 1, x + TOAST_WIDTH, y + height, 0x44000000);

            // Title
            g.drawString(font, toast.title, x + 8, y + 6, LadsPalette.ACCENT, false);

            // Body
            String bodySnippet = toast.body;
            if (font.width(bodySnippet) > TOAST_WIDTH - 16) {
                bodySnippet = font.plainSubstrByWidth(bodySnippet, TOAST_WIDTH - 24) + "...";
            }
            g.drawString(font, bodySnippet, x + 8, y + 18, 0xFFE0E0E0, false);

            // Action hints
            if (toast.acceptAction != null || toast.denyAction != null) {
                String actions = "[Y] Accept   [N] Deny";
                g.drawString(font, actions, x + 8, y + 30, 0xFFAAAAAA, false);
            }

            y += height + TOAST_MARGIN;
        }
    }
}
