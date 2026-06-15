/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 */
package com.thelads.core.client;

import net.minecraft.client.Minecraft;

public class ProfileContext {
    public static String current(Minecraft mc) {
        if (mc.getCurrentServer() != null) {
            return "server:" + mc.getCurrentServer().ip;
        }
        if (mc.getSingleplayerServer() != null) {
            try {
                return "world:" + mc.getSingleplayerServer().getWorldData().getLevelName();
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
        return "menu";
    }

    public static String label(Minecraft mc) {
        String c = ProfileContext.current(mc);
        if (c.startsWith("server:")) {
            return "server: " + c.substring(7);
        }
        if (c.startsWith("world:")) {
            return "world: " + c.substring(6);
        }
        return "(not in a world)";
    }
}

