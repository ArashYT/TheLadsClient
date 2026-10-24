package com.thelads.core.client;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;

/**
 * Quick Reconnect (OneClient's REDACTION quick reconnect and direct ping): a Reconnect button on the disconnected screen that joins
 * the last server again (only when AutoReconnect shows no button of its own), and Direct Connect pings the typed address once you
 * stop typing and shows its MOTD, players, version and ping under the field, as the server list does. Both only act on the player's
 * own click or typing, with the same status ping the server list sends: visual only.
 */
public final class QuickReconnect {
    public static final String NAME = "Quick Reconnect", BUTTON = "Reconnect Button", PING = "Direct Connect Ping";
    /** Direct Connect pings an address left unchanged this long. */
    static final long QUIET = 600_000_000L;
    public static boolean button, ping;
    private static String typed = "", pinged = "";
    private static long typedAt;
    private static Module module;

    private QuickReconnect() {}

    /** Once a client tick. */
    public static void tick() {
        if (module == null) module = ModuleManager.getInstance().getModule(NAME);
        boolean on = module != null && module.isEnabled();
        button = on && module.getOption(BUTTON) instanceof BoolOption b && b.get();
        ping = on && module.getOption(PING) instanceof BoolOption p && p.get();
    }

    /** Direct Connect's field holds {@code address}: the address to ping now (typed, then left alone), or null. */
    public static String due(String address, long now) {
        String text = address == null ? "" : address.trim();
        if (!text.equals(typed)) {
            typed = text;
            typedAt = now;
            return null;
        }
        if (text.isEmpty() || text.equals(pinged) || now - typedAt < QUIET) return null;
        pinged = text;
        return text;
    }

    /** Direct Connect opened or closed: the next address is pinged again. */
    public static void reset() {
        typed = pinged = "";
    }
}
