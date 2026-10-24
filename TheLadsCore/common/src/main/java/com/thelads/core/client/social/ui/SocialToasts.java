package com.thelads.core.client.social.ui;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.gui.LadsPalette;
import com.thelads.core.client.gui.MenuGraphics;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Social toasts (invites, join requests, friend requests) rendered in the top-right corner.
 * Supports hotkeys to accept/deny the newest toast and auto-expires after 15 seconds.
 */
public final class SocialToasts {
    public static final long DEFAULT_DURATION_MS = 15_000L;
    public static final int TOAST_WIDTH = 200;
    public static final int TOAST_HEIGHT = 50;

    public record Toast(
            String id,
            String title,
            String body,
            String acceptAction,
            String denyAction,
            Runnable onAccept,
            Runnable onDeny,
            long createdAt,
            long durationMs
    ) {}

    private static final List<Toast> toasts = new ArrayList<>();
    private static LongSupplier timeSupplier = System::currentTimeMillis;

    private SocialToasts() {}

    public static synchronized void setTimeSupplier(LongSupplier supplier) {
        timeSupplier = supplier != null ? supplier : System::currentTimeMillis;
    }

    public static synchronized void clear() {
        toasts.clear();
    }

    public static synchronized void show(String title, String body, String acceptAction, String denyAction,
                                         Runnable onAccept, Runnable onDeny) {
        long now = timeSupplier.getAsLong();
        Toast toast = new Toast(
                String.valueOf(now) + "_" + toasts.size(),
                title != null ? title : "",
                body != null ? body : "",
                acceptAction,
                denyAction,
                onAccept != null ? onAccept : () -> {},
                onDeny != null ? onDeny : () -> {},
                now,
                DEFAULT_DURATION_MS
        );
        show(toast);
    }

    public static synchronized void show(Toast toast) {
        purgeExpired();
        // Newest first!
        toasts.add(0, toast);
    }

    public static synchronized boolean acceptNewest() {
        purgeExpired();
        if (toasts.isEmpty()) return false;
        Toast newest = toasts.remove(0);
        try {
            newest.onAccept().run();
        } catch (Throwable ignored) {}
        return true;
    }

    public static synchronized boolean denyNewest() {
        purgeExpired();
        if (toasts.isEmpty()) return false;
        Toast newest = toasts.remove(0);
        try {
            newest.onDeny().run();
        } catch (Throwable ignored) {}
        return true;
    }

    public static synchronized List<Toast> activeToasts() {
        purgeExpired();
        return new ArrayList<>(toasts);
    }

    private static void purgeExpired() {
        long now = timeSupplier.getAsLong();
        Iterator<Toast> it = toasts.iterator();
        while (it.hasNext()) {
            Toast t = it.next();
            if (now - t.createdAt() >= t.durationMs()) {
                it.remove();
            }
        }
    }

    public static synchronized void render(LadsGraphics g, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        purgeExpired();
        int maxVisible = 5;
        int count = Math.min(toasts.size(), maxVisible);
        int right = screenWidth - 10;
        int top = 10;

        for (int i = 0; i < count; i++) {
            Toast t = toasts.get(i);
            int tx = right - TOAST_WIDTH;
            int ty = top + i * (TOAST_HEIGHT + 6);

            // Toast background card
            MenuGraphics.round(g, tx, ty, TOAST_WIDTH, TOAST_HEIGHT, LadsPalette.PANEL);
            // Accent top border
            g.fill(tx + 4, ty, tx + TOAST_WIDTH - 4, ty + 1, LadsPalette.ACCENT);

            // Text
            g.drawText(MenuGraphics.fit(g, t.title(), TOAST_WIDTH - 16), tx + 8, ty + 6, LadsPalette.TEXT, true);
            g.drawText(MenuGraphics.fit(g, t.body(), TOAST_WIDTH - 16), tx + 8, ty + 18, LadsPalette.MUTED, false);

            // Action buttons
            boolean hasAccept = t.acceptAction() != null && !t.acceptAction().isEmpty();
            boolean hasDeny = t.denyAction() != null && !t.denyAction().isEmpty();

            int btnY = ty + 30;
            int btnH = 15;

            if (hasAccept && hasDeny) {
                int btnW = (TOAST_WIDTH - 24) / 2;
                int ax = tx + 8;
                int dx = ax + btnW + 8;
                boolean aHover = mouseX >= ax && mouseX < ax + btnW && mouseY >= btnY && mouseY < btnY + btnH;
                boolean dHover = mouseX >= dx && mouseX < dx + btnW && mouseY >= btnY && mouseY < btnY + btnH;

                MenuGraphics.round(g, ax, btnY, btnW, btnH, aHover ? LadsPalette.PRIMARY_HOVER : LadsPalette.PRIMARY);
                g.drawCenteredText(t.acceptAction(), ax + btnW / 2, btnY + (btnH - g.fontHeight()) / 2 + 1, LadsPalette.TEXT, false);

                MenuGraphics.round(g, dx, btnY, btnW, btnH, dHover ? LadsPalette.HOVER : LadsPalette.CARD);
                g.drawCenteredText(t.denyAction(), dx + btnW / 2, btnY + (btnH - g.fontHeight()) / 2 + 1, LadsPalette.MUTED, false);
            } else if (hasAccept) {
                int btnW = TOAST_WIDTH - 16;
                int ax = tx + 8;
                boolean aHover = mouseX >= ax && mouseX < ax + btnW && mouseY >= btnY && mouseY < btnY + btnH;
                MenuGraphics.round(g, ax, btnY, btnW, btnH, aHover ? LadsPalette.PRIMARY_HOVER : LadsPalette.PRIMARY);
                g.drawCenteredText(t.acceptAction(), ax + btnW / 2, btnY + (btnH - g.fontHeight()) / 2 + 1, LadsPalette.TEXT, false);
            }
        }
    }

    public static synchronized boolean mouseClicked(double x, double y, int button, int screenWidth, int screenHeight) {
        if (button != 0) return false;
        purgeExpired();
        int maxVisible = 5;
        int count = Math.min(toasts.size(), maxVisible);
        int right = screenWidth - 10;
        int top = 10;

        for (int i = 0; i < count; i++) {
            Toast t = toasts.get(i);
            int tx = right - TOAST_WIDTH;
            int ty = top + i * (TOAST_HEIGHT + 6);

            if (x >= tx && x < tx + TOAST_WIDTH && y >= ty && y < ty + TOAST_HEIGHT) {
                boolean hasAccept = t.acceptAction() != null && !t.acceptAction().isEmpty();
                boolean hasDeny = t.denyAction() != null && !t.denyAction().isEmpty();
                int btnY = ty + 30;
                int btnH = 15;

                if (hasAccept && hasDeny) {
                    int btnW = (TOAST_WIDTH - 24) / 2;
                    int ax = tx + 8;
                    int dx = ax + btnW + 8;

                    if (x >= ax && x < ax + btnW && y >= btnY && y < btnY + btnH) {
                        toasts.remove(i);
                        try { t.onAccept().run(); } catch (Throwable ignored) {}
                        return true;
                    } else if (x >= dx && x < dx + btnW && y >= btnY && y < btnY + btnH) {
                        toasts.remove(i);
                        try { t.onDeny().run(); } catch (Throwable ignored) {}
                        return true;
                    }
                } else if (hasAccept) {
                    int btnW = TOAST_WIDTH - 16;
                    int ax = tx + 8;
                    if (x >= ax && x < ax + btnW && y >= btnY && y < btnY + btnH) {
                        toasts.remove(i);
                        try { t.onAccept().run(); } catch (Throwable ignored) {}
                        return true;
                    }
                }
                // Click anywhere on toast dismisses it
                toasts.remove(i);
                return true;
            }
        }
        return false;
    }
}
