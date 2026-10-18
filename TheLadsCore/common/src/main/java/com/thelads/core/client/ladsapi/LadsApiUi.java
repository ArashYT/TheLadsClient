package com.thelads.core.client.ladsapi;

/**
 * What the player sees when a server's rules arrive. FROZEN INTERFACE (phase 0): lane CORE calls it, lane GUI implements the bodies
 * (one toast per policy, never one per mod; the settings screen reads ServerPolicy.active() itself for locks and tooltips).
 */
public final class LadsApiUi {
    private LadsApiUi() {}

    /**
     * A new policy is in force. {@code changed} = how many modules/options it blocks, limits or forces; {@code notice} = the server's
     * own text or "". Called on the game thread; shows nothing when changed == 0 and notice is empty, or "Show server notice" is off.
     */
    public static void policyApplied(String serverName, int changed, String notice) {
        // lane GUI
    }

    /** The rules are gone (left the server, switch, reset). Closes the toast if it is still up. */
    public static void policyCleared() {
        // lane GUI
    }
}
