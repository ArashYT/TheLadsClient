package com.thelads.core.client.ladsapi.minigame;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The minigame feature caps (PROTOCOL.md section 8) and which of them this game version implements. A version declares each
 * one once at startup, after its hooks are in place; the hello reply lists exactly the declared ones, and a server message for
 * a cap that was not declared is ignored. Until something is declared nothing changes on the wire.
 */
public final class Caps {
    public static final String INPUT = "input", RUMBLE = "rumble", CAMERA = "camera", FX = "fx", LOCK = "lock",
        WAYPOINT = "waypoint", TELEGRAPH = "telegraph", HUD = "hud", AUDIO = "audio", PROFILE = "profile", RUN = "run",
        GHOST = "ghost", ACTION = "action", DIALOG = "dialog";
    public static final List<String> ALL = List.of(INPUT, RUMBLE, CAMERA, FX, LOCK, WAYPOINT, TELEGRAPH, HUD, AUDIO, PROFILE,
        RUN, GHOST, ACTION, DIALOG);

    private static final Set<String> declared = ConcurrentHashMap.newKeySet();

    private Caps() {}

    /** This build implements {@code cap}. False (and nothing declared) for a name that is not one of the 14. */
    public static boolean declare(String cap) {
        if (cap == null || !ALL.contains(cap)) return false;
        declared.add(cap);
        return true;
    }

    public static boolean isFeature(String name) {
        return name != null && ALL.contains(name);
    }

    public static boolean declared(String cap) {
        return declared.contains(cap);
    }

    /** The declared caps, sorted. */
    public static Set<String> declared() {
        return new TreeSet<>(declared);
    }

    /** The cap a server message type needs, or null when it is not a minigame message (the client sends input, action and dialog, never receives them). */
    public static String capOf(String type) {
        if (type == null) return null;
        String t = type.toLowerCase(Locale.ROOT);
        if (t.equals("actions")) return ACTION;
        return switch (t) {
            case RUMBLE, CAMERA, FX, LOCK, WAYPOINT, TELEGRAPH, HUD, AUDIO, PROFILE, RUN, GHOST, DIALOG -> t;
            default -> null;
        };
    }

    /** For tests and a version that is shut down. */
    public static void clearDeclared() {
        declared.clear();
    }
}
