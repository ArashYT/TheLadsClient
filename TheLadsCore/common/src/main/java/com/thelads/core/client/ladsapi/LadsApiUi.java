package com.thelads.core.client.ladsapi;

import com.thelads.core.client.hud.ServerRulesNotice;
import com.thelads.core.client.hud.ToastAction;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.config.SliderOption;
import java.util.Collections;
import java.util.List;

/**
 * What the player sees when a server's rules arrive. FROZEN INTERFACE (phase 0): lane CORE calls it, lane GUI implements the bodies
 * (one toast per policy, never one per mod; the settings screen reads ServerPolicy.active() itself for locks and tooltips).
 * Extended to support rich server notification toasts with clickable action buttons.
 */
public final class LadsApiUi {
    private static volatile String activeTitle;
    private static volatile String activeNotice;
    private static volatile long activeUntil;
    private static volatile List<ToastAction> activeActions = Collections.emptyList();

    private LadsApiUi() {}

    /**
     * A new policy is in force. {@code changed} = how many modules/options it blocks, limits or forces; {@code notice} = the server's
     * own text or "". Called on the game thread; shows nothing when changed == 0 and notice is empty, or "Show server notice" is off.
     */
    public static void policyApplied(String serverName, int changed, String notice) {
        policyApplied(serverName, changed, notice, Collections.emptyList());
    }

    public static void policyApplied(String serverName, int changed, String notice, List<ToastAction> actions) {
        policyApplied(serverName, changed, 0, notice, actions);
    }

    /** {@code unlocked} = modules whose own safety block the server lifted by name: the toast always says so (never silently). */
    public static void policyApplied(String serverName, int changed, int unlocked, String notice, List<ToastAction> actions) {
        if (!isShowServerNoticeEnabled()) {
            policyCleared();
            return;
        }

        String noticeLine = (notice != null && !notice.trim().isEmpty()) ? notice.trim() : null;
        boolean hasActions = actions != null && !actions.isEmpty();
        if (changed <= 0 && unlocked <= 0 && noticeLine == null && !hasActions) {
            policyCleared();
            return;
        }

        String title = formatPolicyToastTitle(serverName, changed, unlocked);
        activeTitle = title;
        activeNotice = noticeLine;
        activeActions = hasActions ? actions : Collections.emptyList();
        activeUntil = System.nanoTime() + (hasActions ? 14_000_000_000L : 8_000_000_000L);
        ServerRulesNotice.showPolicyToast(title, noticeLine, activeActions);
    }

    /** Direct notification toast with custom actions triggered from server or client events. */
    public static void showNotification(String title, String notice, List<ToastAction> actions) {
        if (!isShowServerNoticeEnabled()) return;
        activeTitle = title;
        activeNotice = (notice != null && !notice.trim().isEmpty()) ? notice.trim() : null;
        activeActions = (actions != null && !actions.isEmpty()) ? actions : Collections.emptyList();
        activeUntil = System.nanoTime() + (!activeActions.isEmpty() ? 14_000_000_000L : 8_000_000_000L);
        ServerRulesNotice.showPolicyToast(title, activeNotice, activeActions);
    }

    /** The rules are gone (left the server, switch, reset). Closes the toast if it is still up. */
    public static void policyCleared() {
        activeTitle = null;
        activeNotice = null;
        activeUntil = 0;
        activeActions = Collections.emptyList();
        ServerRulesNotice.clearPolicyToast();
    }

    /** True if a policy toast is currently showing and has not expired. */
    public static boolean isToastActive() {
        return activeTitle != null && (System.nanoTime() - activeUntil < 0);
    }

    public static String activeToastTitle() {
        return isToastActive() ? activeTitle : null;
    }

    public static String activeToastNotice() {
        return isToastActive() ? activeNotice : null;
    }

    public static List<ToastAction> activeToastActions() {
        return isToastActive() ? activeActions : Collections.emptyList();
    }

    /** Formats the title line for a policy notification toast ("Lads SMP limits 3 Lads settings"). */
    public static String formatPolicyToastTitle(String serverName, int changed) {
        return formatPolicyToastTitle(serverName, changed, 0);
    }

    /** "Lads SMP limits 3 Lads settings", "... and unlocks 1", "Lads SMP unlocks 2 Lads settings" (an allow is not a limit). */
    public static String formatPolicyToastTitle(String serverName, int changed, int unlocked) {
        String server = (serverName == null || serverName.trim().isEmpty()) ? "This server" : serverName.trim();
        if (changed <= 0 && unlocked <= 0) {
            return server;
        }
        if (changed <= 0) {
            return server + " unlocks " + unlocked + (unlocked == 1 ? " Lads setting" : " Lads settings");
        }
        return server + " limits " + changed + (changed == 1 ? " Lads setting" : " Lads settings") + (unlocked > 0 ? " and unlocks " + unlocked : "");
    }

    /**
     * Formats the tooltip text for an option subject to a ServerPolicy rule:
     * "Limited by <server>: max 10%" / "min 2" / "Set by <server>" / "Only: A, B".
     */
    public static String formatOptionTooltip(String serverName, OptionRule rule, Option option) {
        if (rule == null) return "";
        String server = (serverName == null || serverName.trim().isEmpty()) ? "server" : serverName.trim();
        if (rule.forcesValue()) {
            return "Set by " + server;
        }
        String suffix = "";
        if (option instanceof SliderOption s && s.display().endsWith("%")) {
            suffix = "%";
        }
        boolean hasMin = !Double.isNaN(rule.min);
        boolean hasMax = !Double.isNaN(rule.max);
        if (hasMin && hasMax) {
            return "Limited by " + server + ": min " + SliderOption.format(rule.min) + suffix + ", max " + SliderOption.format(rule.max) + suffix;
        } else if (hasMax) {
            return "Limited by " + server + ": max " + SliderOption.format(rule.max) + suffix;
        } else if (hasMin) {
            return "Limited by " + server + ": min " + SliderOption.format(rule.min) + suffix;
        }
        if (rule.choices != null && rule.choices.length > 0) {
            return "Limited by " + server + ": Only: " + String.join(", ", rule.choices);
        }
        if (rule.lock) {
            return "Set by " + server;
        }
        return "Limited by " + server;
    }

    /**
     * Module rows: module(name) == BLOCK -> "Blocked by <server>",
     * == ON -> "Turned on by <server>" (unless refused).
     */
    public static String formatModuleStatus(String serverName, ModuleState state, boolean refusesChanges) {
        String server = (serverName == null || serverName.trim().isEmpty()) ? "server" : serverName.trim();
        if (state == ModuleState.BLOCK) {
            return "Blocked by " + server;
        }
        if (state == ModuleState.ON && !refusesChanges) {
            return "Turned on by " + server;
        }
        return null;
    }

    /**
     * Look up the "Show server notice" setting from module "Server Control", defaulting to true if absent.
     */
    public static boolean isShowServerNoticeEnabled() {
        try {
            Module sc = ModuleManager.getInstance().getModule("Server Control");
            if (sc != null) {
                Option opt = sc.getOption("Show server notice");
                if (opt instanceof BoolOption b) {
                    return b.get();
                }
            }
        } catch (Throwable ignored) {}
        return true;
    }
}
