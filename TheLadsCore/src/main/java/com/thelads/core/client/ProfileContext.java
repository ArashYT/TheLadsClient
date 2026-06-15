package com.thelads.core.client;

import net.minecraft.client.Minecraft;

/** Computes the current profile context key (server / world / menu). */
public class ProfileContext {
    public static String current(Minecraft mc) {
        if (mc.getCurrentServer() != null) {
            return "server:" + mc.getCurrentServer().ip;
        }
        if (mc.getSingleplayerServer() != null) {
            try {
                return "world:" + mc.getSingleplayerServer().getWorldData().getLevelName();
            } catch (Exception ignored) {
            }
        }
        return "menu";
    }

    /** Human-readable label for the current context (for the Profiles UI). */
    public static String label(Minecraft mc) {
        String c = current(mc);
        if (c.startsWith("server:")) {
            return "server: " + c.substring(7);
        }
        if (c.startsWith("world:")) {
            return "world: " + c.substring(6);
        }
        return "(not in a world)";
    }
}
