package com.thelads.core.client.social.ui;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.gui.LadsPalette;
import com.thelads.core.client.gui.MenuGraphics;
import com.thelads.core.client.social.SocialState;
import java.util.ArrayList;
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
    private static int reservedTop;
    private static long reservedAt = Long.MIN_VALUE / 2;

    private SocialToasts() {}

    public static synchronized void setTimeSupplier(LongSupplier supplier) {
        timeSupplier = supplier != null ? supplier : System::currentTimeMillis;
    }

    public static synchronized void clear() {
        toasts.clear();
        reservedAt = Long.MIN_VALUE / 2; // no screen reserve
    }

    public static synchronized void show(String title, String body, String acceptAction, String denyAction,
                                         Runnable onAccept, Runnable onDeny) {
        show(null, title, body, acceptAction, denyAction, onAccept, onDeny);
    }

    /** id: the SocialState action id the buttons run (purgeExpired drops the toast once it is no longer pending), or null. */
    private static void show(String id, String title, String body, String acceptAction, String denyAction, Runnable onAccept, Runnable onDeny) {
        long now = timeSupplier.getAsLong();
        Toast toast = new Toast(
                id != null ? id : now + "_" + toasts.size(),
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

    /** SocialBridge.toast: the buttons run SocialState action ids (null = no button) on the client; nothing goes to a game server. */
    public static synchronized void showAction(String title, String body, String acceptAction, String denyAction) {
        // A toast without buttons replaces the last one with the same title (progress: "Backing up the world: 40%")
        if (acceptAction == null && denyAction == null) toasts.removeIf(t -> t.title().equals(title) && t.acceptAction() == null && t.denyAction() == null);
        String acceptLabel = acceptAction == null ? null
                : acceptAction.startsWith("invite_accept:") || acceptAction.startsWith("p2p_join:") ? "Join"
                : acceptAction.startsWith("dm:") ? "Reply" : "Accept";
        show(acceptAction != null ? acceptAction : denyAction, title, body, acceptLabel, denyAction == null ? null : "Decline",
                action(acceptAction), action(denyAction));
    }

    private static Runnable action(String id) {
        return id == null ? null : () -> SocialState.get().handleAction(id);
    }

    /** An open Friends/Host screen calls this every frame with its header's bottom: toasts stay below its tabs and buttons. */
    public static synchronized void reserveTop(int y) {
        reservedTop = y;
        reservedAt = timeSupplier.getAsLong();
    }

    private static int top() {
        return timeSupplier.getAsLong() - reservedAt < 500 ? Math.max(10, reservedTop) : 10;
    }

    /** Checked before building a graphics adapter, so the HUD allocates nothing while there is no toast. */
    public static synchronized boolean isEmpty() {
        return toasts.isEmpty();
    }

    public static synchronized void show(Toast toast) {
        purgeExpired();
        // Newest first; at most 10 (a flood of requests can't grow it)
        toasts.add(0, toast);
        while (toasts.size() > 10) toasts.remove(toasts.size() - 1);
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
        if (toasts.isEmpty()) return;
        long now = timeSupplier.getAsLong();
        for (int i = toasts.size() - 1; i >= 0; i--) {
            Toast t = toasts.get(i);
            // An action id (showAction) whose request was answered elsewhere or expired: no stale buttons
            if (now - t.createdAt() >= t.durationMs() || t.id().indexOf(':') > 0 && !SocialState.get().actionPending(t.id())) {
                toasts.remove(i);
            }
        }
    }

    public static synchronized void render(LadsGraphics g, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        if (toasts.isEmpty()) return;
        purgeExpired();
        if (toasts.isEmpty()) return;

        int maxVisible = 5;
        int count = Math.min(toasts.size(), maxVisible);
        int right = screenWidth - 10;
        int top = top();

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
        if (button != 0 || toasts.isEmpty()) return false;
        purgeExpired();
        if (toasts.isEmpty()) return false;

        int maxVisible = 5;
        int count = Math.min(toasts.size(), maxVisible);
        int right = screenWidth - 10;
        int top = top();

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
