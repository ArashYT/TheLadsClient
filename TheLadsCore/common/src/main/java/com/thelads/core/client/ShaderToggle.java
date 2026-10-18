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
 * <p>
 * 1.20.1 has no Vulkan and stays on OpenGL, but Iris is suspended the same way until shaders are on: choice "OpenGL" = shaders on,
 * "Vulkan" = shaders off (Iris suspended), and the screens say "Iris" instead of the renderer ({@code vulkan} false below).
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

    /** What a toggle screen is for. TURN_ON: the confirm behind "Shader packs..." (shaders need Iris loaded). TURN_OFF: the player
     *  asked for shaders off. WARN_OFF: Iris is loaded but no pack is in use, so it only costs FPS. */
    public enum Mode {
        TURN_ON, TURN_OFF, WARN_OFF;

        /** The restart brings Iris up (true) or leaves it suspended (false). */
        public boolean on() { return this == TURN_ON; }
    }

    public static String title(Mode mode) {
        return switch (mode) {
            case TURN_ON -> "Turn on shaders?";
            case TURN_OFF -> "Turn off shaders?";
            case WARN_OFF -> "Shaders are off";
        };
    }

    /** {@code vulkan}: 26.x, where shaders mean OpenGL instead of Vulkan; false on 1.20.1. {@code destination}: where the game comes back to. */
    public static String body(Mode mode, boolean vulkan, boolean launcher, String destination) {
        String back = launcher ? "Minecraft will restart and bring you back to " + destination + "." : "Minecraft will close; start it again from the launcher";
        if (!launcher) back += mode == Mode.TURN_ON ? " to use shaders." : ".";
        return switch (mode) {
            case TURN_ON -> (vulkan ? "Shaders need OpenGL, which is slower than Vulkan. " : "Shaders need Iris, which Minecraft only loads at startup. ") + back;
            case TURN_OFF -> (vulkan ? "Minecraft will switch back to Vulkan, which is faster. " : "Iris will be unloaded so the game runs faster. ") + back;
            case WARN_OFF -> (vulkan ? "Minecraft is still running on OpenGL for Iris, which costs a lot of FPS. " : "Iris is still loaded, which costs some FPS. ")
                + (launcher ? "Restart now to " + (vulkan ? "switch back to Vulkan" : "unload it") + ", or keep " + (vulkan ? "OpenGL" : "it") + " until you next start the game."
                    : "Quit now and start it again from the launcher to " + (vulkan ? "switch back to Vulkan" : "unload it") + ", or keep " + (vulkan ? "OpenGL" : "it") + " until you next start the game.");
        };
    }

    public static String restartLabel(Mode mode, boolean vulkan, boolean launcher) {
        if (!launcher) return "Quit now";
        return switch (mode) {
            case TURN_ON -> "Restart with shaders";
            case TURN_OFF -> vulkan ? "Restart on Vulkan" : "Restart without shaders";
            case WARN_OFF -> vulkan ? "Restart now (Vulkan)" : "Restart now (shaders off)";
        };
    }

    public static String cancelLabel(Mode mode, boolean vulkan) {
        return mode == Mode.WARN_OFF ? (vulkan ? "Keep OpenGL until next launch" : "Keep Iris until next launch") : "Cancel";
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
