package com.thelads.core.client;

import java.util.Locale;

/** The Lads Network: our own server, joined from the title screen and named in Discord when its LadsClientAPI says hello. */
public final class LadsNetwork {
    public static final String NAME = "The Lads Network";
    public static final String ADDRESS = "mc.arashyt.ca";
    /** The title screen's grid cells fit about 9 characters ("The Lads Network" showed as "The Lad..."). */
    public static final String TITLE_LABEL = "Network";
    public static final String TOOLTIP = "Join " + NAME + " (" + ADDRESS + ")";

    private LadsNetwork() {
    }

    /** The title screen offers the network in the client profiles, not in a modpack (the launcher passes -Dlads.modpack=true). */
    public static boolean titleButton() {
        return !Boolean.getBoolean("lads.modpack");
    }

    /** {@code host} (no port) is arashyt.ca or one of its subdomains: every server of the network goes through it. */
    public static boolean isHost(String host) {
        if (host == null) return false;
        String h = host.trim().toLowerCase(Locale.ROOT);
        return h.equals("arashyt.ca") || h.endsWith(".arashyt.ca");
    }
}
