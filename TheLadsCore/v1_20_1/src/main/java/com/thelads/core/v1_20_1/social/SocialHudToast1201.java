package com.thelads.core.v1_20_1.social;

import com.thelads.core.client.gui.LadsPalette;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/**
 * Renders Lads social toasts (invites, join requests, friend requests) directly on the HUD layer (top right).
 * Supports mouse clicks on Accept / Deny buttons and keybinds.
 */
public final class SocialHudToast1201 {
    public static final class ToastItem {
        final String title;
        final String body;
        final String acceptAction;
        final String denyAction;
        final long createdAt;
        final long lifetimeMillis;
        int lastX;
        int lastY;

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
    private static final int TOAST_HEIGHT = 46;
    private static final int TOAST_MARGIN = 6;
    private static final long DEFAULT_LIFETIME = 15_000L; // 15 seconds

    private SocialHudToast1201() {}

    public static synchronized void addToast(String title, String body, String acceptAction, String denyAction) {
        ToastItem item = new ToastItem(title, body, acceptAction, denyAction, DEFAULT_LIFETIME);
        item.lastX = 0;
        item.lastY = TOAST_MARGIN;
        ACTIVE_TOASTS.add(0, item);
        if (ACTIVE_TOASTS.size() > 5) {
            ACTIVE_TOASTS.remove(ACTIVE_TOASTS.size() - 1);
        }
    }

    public static synchronized boolean acceptNewest() {
        if (!ACTIVE_TOASTS.isEmpty()) {
            ToastItem toast = ACTIVE_TOASTS.remove(0);
            if (toast.acceptAction != null) {
                SocialBridge1201.handleChatAction(toast.acceptAction);
                playClickSound();
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
                playClickSound();
                return true;
            }
        }
        return false;
    }

    public static synchronized boolean onClick(double mouseX, double mouseY) {
        Iterator<ToastItem> it = ACTIVE_TOASTS.iterator();
        while (it.hasNext()) {
            ToastItem toast = it.next();
            if (toast.isExpired()) continue;

            int x = toast.lastX;
            int y = toast.lastY;

            // Accept button bounds: [x + 8, y + 28] to [x + 72, y + 42]
            if (toast.acceptAction != null && mouseX >= x + 8 && mouseX <= x + 72 && mouseY >= y + 28 && mouseY <= y + 42) {
                it.remove();
                SocialBridge1201.handleChatAction(toast.acceptAction);
                playClickSound();
                return true;
            }

            // Deny button bounds: [x + 78, y + 28] to [x + 142, y + 42]
            if (toast.denyAction != null && mouseX >= x + 78 && mouseX <= x + 142 && mouseY >= y + 28 && mouseY <= y + 42) {
                it.remove();
                SocialBridge1201.handleChatAction(toast.denyAction);
                playClickSound();
                return true;
            }
        }
        return false;
    }

    private static void playClickSound() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
        } catch (Throwable ignored) {}
    }

    public static synchronized void render(GuiGraphics g, float tickDelta) {
        if (ACTIVE_TOASTS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null && mc.options.hideGui) return;

        Font font = mc.font;
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int y = TOAST_MARGIN;

        double mouseX = -1;
        double mouseY = -1;
        if (mc.mouseHandler != null && mc.getWindow() != null && mc.getWindow().getScreenWidth() > 0 && mc.getWindow().getScreenHeight() > 0) {
            mouseX = mc.mouseHandler.xpos() * (double) mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth();
            mouseY = mc.mouseHandler.ypos() * (double) mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getScreenHeight();
        }

        Iterator<ToastItem> it = ACTIVE_TOASTS.iterator();
        while (it.hasNext()) {
            ToastItem toast = it.next();
            if (toast.isExpired()) {
                it.remove();
                continue;
            }

            int x = screenWidth - TOAST_WIDTH - TOAST_MARGIN;
            toast.lastX = x;
            toast.lastY = y;
            int height = TOAST_HEIGHT;

            // Background card with Lads border
            g.fill(x, y, x + TOAST_WIDTH, y + height, 0xEE141014);
            g.fill(x, y, x + 2, y + height, 0xFFCF1535); // Left accent bar
            g.fill(x, y, x + TOAST_WIDTH, y + 1, 0x44FFFFFF);
            g.fill(x, y + height - 1, x + TOAST_WIDTH, y + height, 0x44000000);

            // Title
            g.drawString(font, toast.title, x + 8, y + 5, LadsPalette.ACCENT, false);

            // Body
            String bodySnippet = toast.body;
            if (font.width(bodySnippet) > TOAST_WIDTH - 16) {
                bodySnippet = font.plainSubstrByWidth(bodySnippet, TOAST_WIDTH - 24) + "...";
            }
            g.drawString(font, bodySnippet, x + 8, y + 16, 0xFFE0E0E0, false);

            // Accept / Deny Buttons
            if (toast.acceptAction != null || toast.denyAction != null) {
                if (toast.acceptAction != null) {
                    boolean acceptHover = mouseX >= x + 8 && mouseX <= x + 72 && mouseY >= y + 28 && mouseY <= y + 42;
                    int acceptBg = acceptHover ? 0xFF2E6E34 : 0xFF1B4320;
                    g.fill(x + 8, y + 28, x + 72, y + 42, acceptBg);
                    g.fill(x + 8, y + 28, x + 72, y + 29, 0xFF55FF55);
                    g.fill(x + 8, y + 41, x + 72, y + 42, 0xFF55FF55);
                    g.drawString(font, "Accept", x + 20, y + 31, 0xFFFFFFFF, false);
                }

                if (toast.denyAction != null) {
                    boolean denyHover = mouseX >= x + 78 && mouseX <= x + 142 && mouseY >= y + 28 && mouseY <= y + 42;
                    int denyBg = denyHover ? 0xFF7D2020 : 0xFF481414;
                    g.fill(x + 78, y + 28, x + 142, y + 42, denyBg);
                    g.fill(x + 78, y + 28, x + 142, y + 29, 0xFFFF5555);
                    g.fill(x + 78, y + 41, x + 142, y + 42, 0xFFFF5555);
                    g.drawString(font, "Deny", x + 96, y + 31, 0xFFFFFFFF, false);
                }
            }

            y += height + TOAST_MARGIN;
        }
    }
}
