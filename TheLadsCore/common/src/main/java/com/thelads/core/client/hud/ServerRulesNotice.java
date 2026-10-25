package com.thelads.core.client.hud;

import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Joining a server that turns modules the player has on off (its rules ban them, or we don't know its rules: HypixelSafetyManager)
 * says so at the top of the screen for a few seconds, once per join, so a minimap does not just vanish.
 * Also renders the LadsClientAPI server policy toast and rich server notification toasts with interactive clickable action buttons.
 * Action buttons are gated under HypixelSafetyManager ("Server Notification Actions").
 */
public final class ServerRulesNotice {
    private static final long SHOW_NANOS = 8_000_000_000L;
    private static final long SHOW_ACTIONS_NANOS = 14_000_000_000L;
    private static String seen = "";
    private static String text;
    private static long until;

    private static String policyTitle;
    private static String policyNotice;
    private static long policyUntil;
    private static List<ToastAction> toastActions = Collections.emptyList();

    public static final class ActionHitbox {
        public final int x, y, width, height;
        public final ToastAction action;

        public ActionHitbox(int x, int y, int width, int height, ToastAction action) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.action = action;
        }
    }

    private static final List<ActionHitbox> activeHitboxes = new ArrayList<>();

    private ServerRulesNotice() {}

    public static void showPolicyToast(String title, String notice) {
        showPolicyToast(title, notice, Collections.emptyList());
    }

    public static void showPolicyToast(String title, String notice, List<ToastAction> actions) {
        policyTitle = title;
        policyNotice = notice;
        toastActions = (actions != null && !actions.isEmpty()) ? new ArrayList<>(actions) : Collections.emptyList();
        policyUntil = System.nanoTime() + (!toastActions.isEmpty() ? SHOW_ACTIONS_NANOS : SHOW_NANOS);
    }

    public static void clearPolicyToast() {
        policyTitle = null;
        policyNotice = null;
        policyUntil = 0;
        toastActions = Collections.emptyList();
        activeHitboxes.clear();
    }

    public static boolean isToastShowing() {
        return policyTitle != null && (System.nanoTime() - policyUntil < 0);
    }

    public static List<ToastAction> getToastActions() {
        return Collections.unmodifiableList(toastActions);
    }

    public static List<ActionHitbox> getActiveHitboxes() {
        return Collections.unmodifiableList(activeHitboxes);
    }

    public static boolean isActionsEnabled() {
        Module m = ModuleManager.getInstance().getModule("Server Notification Actions");
        if (m == null || !m.isEnabled()) return false;
        return !HypixelSafetyManager.isDisallowed("Server Notification Actions");
    }

    /** Every frame: an address compare, then nothing unless a notice is showing. */
    static void render(LadsGraphics g) {
        LadsGameBridge game = g.getGame();
        if (game == null || !game.isIngame()) return;

        // 1. LadsClientAPI policy / notification toast (higher priority)
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
        activeHitboxes.clear();
        int tw = g.textWidth(title);
        int nw = (notice != null && !notice.isEmpty()) ? g.textWidth(notice) : 0;

        boolean showActions = !toastActions.isEmpty() && isActionsEnabled();
        int buttonsTotalWidth = 0;
        int[] btnWidths = null;
        if (showActions) {
            btnWidths = new int[toastActions.size()];
            for (int i = 0; i < toastActions.size(); i++) {
                int bw = Math.max(30, g.textWidth(toastActions.get(i).getLabel()) + 10);
                btnWidths[i] = bw;
                buttonsTotalWidth += bw + (i > 0 ? 4 : 0);
            }
        }

        int w = Math.max(Math.max(tw, nw), buttonsTotalWidth) + 16;
        int x = Math.max(2, (g.getScaledWidth() - w) / 2);
        boolean twoLines = notice != null && !notice.isEmpty();
        int contentH = twoLines ? 28 : 16;
        int h = showActions ? (contentH + 16) : contentH;
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

        // Action buttons
        if (showActions && btnWidths != null) {
            int btnY = y + contentH + 1;
            int btnX = x + 8;
            for (int i = 0; i < toastActions.size(); i++) {
                ToastAction action = toastActions.get(i);
                int bw = btnWidths[i];
                int bh = 12;

                // Button card & border
                g.fill(btnX, btnY, btnX + bw, btnY + bh, 0xD0241A24);
                g.fill(btnX, btnY, btnX + bw, btnY + 1, 0x80FFAA55);
                g.fill(btnX, btnY + bh - 1, btnX + bw, btnY + bh, 0x80FFAA55);
                g.fill(btnX, btnY, btnX + 1, btnY + bh, 0x80FFAA55);
                g.fill(btnX + bw - 1, btnY, btnX + bw, btnY + bh, 0x80FFAA55);

                // Button label
                g.drawCenteredText(action.getLabel(), btnX + bw / 2, btnY + 2, 0xFFFFFFFF, false);

                activeHitboxes.add(new ActionHitbox(btnX, btnY, bw, bh, action));
                btnX += bw + 4;
            }
        }
    }

    /**
     * Handles mouse clicks on active toast notification action buttons.
     * Returns true if a button was clicked and handled.
     */
    public static boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0) return false;
        if (!isToastShowing()) return false;
        if (!isActionsEnabled()) return false;

        for (ActionHitbox hitbox : activeHitboxes) {
            if (mouseX >= hitbox.x && mouseX < hitbox.x + hitbox.width
                    && mouseY >= hitbox.y && mouseY < hitbox.y + hitbox.height) {
                executeAction(hitbox.action);
                return true;
            }
        }
        return false;
    }

    public static void executeAction(ToastAction action) {
        if (action == null) return;
        switch (action.getType()) {
            case RUN_COMMAND -> {
                String cmd = action.getPayload().trim();
                if (!cmd.isEmpty()) {
                    if (!cmd.startsWith("/")) cmd = "/" + cmd;
                    LadsGameBridge.get().sendChatLine(cmd);
                }
                clearPolicyToast();
            }
            case SUGGEST_COMMAND -> {
                String cmd = action.getPayload().trim();
                if (!cmd.isEmpty()) {
                    if (!cmd.startsWith("/")) cmd = "/" + cmd;
                    LadsGameBridge.get().openChatWithText(cmd);
                }
                clearPolicyToast();
            }
            case OPEN_URL -> {
                String url = action.getPayload().trim();
                if (LadsGameBridge.isSafeUrl(url)) {
                    LadsGameBridge.get().openUrlWithConfirmation(url);
                }
                clearPolicyToast();
            }
            case DISMISS -> {
                clearPolicyToast();
            }
        }
    }

    static String blockedText() {
        List<String> names = new ArrayList<>();
        // A LadsClientAPI block (Block.SERVER) is in the policy toast already and cannot be bypassed: not listed here.
        for (Module m : ModuleManager.getInstance().getModules())
            if (m.isEnabledSetting() && m.isBlockedByServer()
                    && HypixelSafetyManager.blockOf(m.getName()) != HypixelSafetyManager.Block.SERVER)
                names.add(m.getName());
        return names.isEmpty() ? null : String.join(", ", names) + " off on this server (server rules). Lads Settings > Bypass, at your own risk.";
    }
}
