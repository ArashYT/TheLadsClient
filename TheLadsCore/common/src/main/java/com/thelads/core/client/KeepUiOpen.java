package com.thelads.core.client;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;

/**
 * Keep UI Open: the server cannot close your chat or your own inventory. A server close-container packet leaves an open chat or
 * player inventory alone (real containers still close); a chat pushed away by a death or loading screen (Hypixel game switches,
 * respawns) comes back with what you had typed once that screen closes. Each version feeds it its screen changes and close packets.
 */
public final class KeepUiOpen {
    public static final String MODULE_NAME = "KeepUIOpen", CHAT = "Keep chat open", INVENTORY = "Keep inventory open";
    /**
     * The server-rules name of Keep inventory open: the next clicks go to an inventory the server (and its anticheat) closed, so it
     * acts only in singleplayer, LAN and trusted servers (HypixelSafetyManager). The chat part sends nothing: everywhere.
     */
    public static final String INVENTORY_GATE = "KeepInventoryOpen";

    /** What a screen is, as far as this cares: none, chat, a death or loading screen that pushes chat away, or anything else. */
    public enum Kind { NONE, CHAT, TRANSITION, OTHER }

    /** The chat text pushed away by a death or loading screen, waiting for that screen to close; null when nothing waits. */
    private static String saved;

    private KeepUiOpen() {}

    public static Module module() {
        Module m = new Module(MODULE_NAME, "The server can't close your chat or inventory: deaths, respawns and game switches bring your "
            + "chat back with what you typed, and a server close leaves your own inventory open (chests still close). Keep inventory open "
            + "only works in singleplayer, LAN and servers known to allow it.");
        m.addOption(new BoolOption(CHAT, true));
        m.addOption(new BoolOption(INVENTORY, true));
        m.setEnabled(true);
        return m;
    }

    private static boolean on(String option) {
        Module m = ModuleManager.getInstance().getModule(MODULE_NAME);
        if (m == null || !m.isEnabled()) return false;
        Option o = m.getOption(option);
        return o instanceof BoolOption b && b.get();
    }

    /** A server close-container packet arrived: true to ignore it ({@code current} open, {@code ownInventory}: it is your inventory). */
    public static boolean keepOnServerClose(Kind current, boolean ownInventory) {
        return current == Kind.CHAT && on(CHAT) || ownInventory && on(INVENTORY) && !HypixelSafetyManager.isDisallowed(INVENTORY_GATE);
    }

    /**
     * The screen is about to change from {@code current} (chat showing {@code chatText}) to {@code next}: the chat text to open a
     * chat screen with instead of {@code next}, or null to let the change happen.
     */
    public static String screenChange(Kind current, String chatText, Kind next) {
        if (!on(CHAT)) return saved = null;
        if (current == Kind.CHAT && next == Kind.TRANSITION) {
            saved = chatText == null ? "" : chatText;
            return null;
        }
        if (saved == null || next == Kind.TRANSITION) return null; // nothing waiting, or still loading
        String text = current == Kind.TRANSITION && next == Kind.NONE ? saved : null;
        saved = null; // anything else (another screen, disconnect, chat opened anyway) forgets it
        return text;
    }
}
