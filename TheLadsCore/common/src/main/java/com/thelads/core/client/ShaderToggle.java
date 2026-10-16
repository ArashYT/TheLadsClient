package com.thelads.core.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.core.mods.ModStateStore;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Shaders on demand (26.2/26.3, artifacts/smp-opt/SHADER-TOGGLE-SPEC.md): Vulkan is the default and the launcher suspends Iris for
 * it, recording that in {@link #STATE_FILE} ({"Vulkan":true,"SuspendedMods":["iris"]}). The game writes the renderer the next launch
 * uses ({@link #CHOICE_FILE}) and, before it quits to switch, where to come back to ({@link #RELAUNCH_FILE}); the launcher reads
 * both. All files live in the game directory.
 */
public final class ShaderToggle {
    public static final String STATE_FILE = "lads-renderer.json";
    public static final String CHOICE_FILE = "lads-renderer-choice.json";
    public static final String RELAUNCH_FILE = "lads-relaunch.json";

    private ShaderToggle() {}

    /** The launcher wrote its renderer state, so it starts the game again when it quits with a relaunch request. */
    public static boolean launcherStarted(Path gameDir) {
        return Files.isRegularFile(gameDir.resolve(STATE_FILE));
    }

    /** Iris is installed but the launcher suspended it for Vulkan this launch. */
    public static boolean irisSuspended(Path gameDir) {
        try {
            JsonElement root = JsonParser.parseString(new String(Files.readAllBytes(gameDir.resolve(STATE_FILE)), StandardCharsets.UTF_8));
            if (!root.isJsonObject() || !root.getAsJsonObject().has("SuspendedMods") || !root.getAsJsonObject().get("SuspendedMods").isJsonArray()) return false;
            for (JsonElement id : root.getAsJsonObject().getAsJsonArray("SuspendedMods"))
                if (id.isJsonPrimitive() && "iris".equals(id.getAsString())) return true;
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /** The renderer the launcher starts this game directory with from now on. Returns false when nothing was written. */
    public static boolean choose(Path gameDir, boolean vulkan) {
        JsonObject obj = new JsonObject();
        obj.addProperty("renderer", vulkan ? "Vulkan" : "OpenGL");
        return write(gameDir.resolve(CHOICE_FILE), obj);
    }

    /** Asks the launcher to start the game again once it has quit, into the save folder {@code world} or {@code server} (host[:port]) when given. */
    public static boolean requestRelaunch(Path gameDir, boolean shadersOn, String world, String server) {
        JsonObject obj = new JsonObject();
        obj.addProperty("reason", shadersOn ? "shaders-on" : "shaders-off");
        obj.addProperty("world", world);
        obj.addProperty("server", world == null ? server : null);
        return write(gameDir.resolve(RELAUNCH_FILE), obj);
    }

    private static boolean write(Path file, JsonObject obj) {
        try {
            ModStateStore.writeAtomically(file, obj.toString());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public enum Event { NONE, OFF, ON }

    /** Watches Iris once per tick for the "shaders are off" warning: shown once per toggle, never while its shader pack screen is open. */
    public static final class Watcher {
        private boolean packScreen, inUse, warned;

        /** OFF: show the warning now. ON: shaders came back after a warning, so the next launch keeps OpenGL again. */
        public Event update(boolean onPackScreen, boolean packInUse) {
            boolean left = packScreen && !onPackScreen, turnedOff = inUse && !packInUse;
            packScreen = onPackScreen;
            inUse = packInUse;
            if (packInUse) {
                if (!warned) return Event.NONE;
                warned = false;
                return Event.ON;
            }
            if (onPackScreen || warned || !(left || turnedOff)) return Event.NONE;
            warned = true;
            return Event.OFF;
        }
    }
}
