package com.thelads.core.client.ladsapi;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.UUID;

/**
 * What the server said about spectating (PROTOCOL.md section 7). The client finds nothing out by itself: the target is the
 * entity the server points the camera at anyway, and the state is gone as soon as the server says so, changes or disconnects.
 * The version adapters read it for the camera, the HUD and the target keys. Game thread only.
 */
public final class SpectateState {
    public enum Mode { OFF, FREE, POV }

    private static final SpectateState NONE = new SpectateState(Mode.OFF, null, "", false);
    private static SpectateState active = NONE;

    public final Mode mode;
    /** The player whose eyes the camera is in: set in POV, null otherwise. */
    public final UUID target;
    /** The server's name for the target, or "" (then the HUD uses the entity's own name). */
    public final String name;
    /** Whether the server takes requests for the next or previous target. */
    public final boolean canSwitch;

    private SpectateState(Mode mode, UUID target, String name, boolean canSwitch) {
        this.mode = mode;
        this.target = target;
        this.name = name;
        this.canSwitch = canSwitch;
    }

    /** Never null: {@link Mode#OFF} while the server has said nothing. */
    public static SpectateState active() {
        return active;
    }

    public boolean on() {
        return mode != Mode.OFF;
    }

    public boolean pov() {
        return mode == Mode.POV;
    }

    static void set(SpectateState state) {
        active = state == null ? NONE : state;
    }

    static void clear() {
        active = NONE;
    }

    /**
     * A server message as a state. Anything this client cannot use is "off": an unknown mode from a newer plugin, or a POV
     * without a target. The server's own camera packets then do the work, as for a client without this feature.
     */
    static SpectateState parse(JsonObject json) {
        String mode = text(json, "mode");
        boolean canSwitch = json.has("switch") && json.get("switch").isJsonPrimitive()
            && json.get("switch").getAsJsonPrimitive().isBoolean() && json.get("switch").getAsBoolean();
        if ("free".equals(mode)) return new SpectateState(Mode.FREE, null, "", canSwitch);
        if (!"pov".equals(mode)) return NONE;
        String id = text(json, "target");
        UUID target = id == null ? null : LadsApiClient.parseUuid(id);
        if (target == null) return NONE;
        return new SpectateState(Mode.POV, target, cleanName(text(json, "name")), canSwitch);
    }

    /** At most 32 characters for the HUD, without formatting codes or control characters. */
    static String cleanName(String raw) {
        if (raw == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < raw.length() && out.length() < 32; i++) {
            char c = raw.charAt(i);
            if (c == '§') {
                i++; // the code character goes too
            } else if (c >= ' ' && c != 0x7f) {
                out.append(c);
            }
        }
        return out.toString().trim();
    }

    private static String text(JsonObject json, String key) {
        JsonElement e = json.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : null;
    }
}
