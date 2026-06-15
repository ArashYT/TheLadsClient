/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.Gson
 *  com.google.gson.JsonElement
 *  com.google.gson.JsonObject
 *  net.minecraft.client.Minecraft
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.Holder
 *  net.minecraft.resources.ResourceKey
 */
package com.thelads.core.client;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.FileWriter;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;

public class ScreenshotMeta {
    public static void write(File png) {
        try {
            Minecraft mc = Minecraft.getInstance();
            JsonObject j = new JsonObject();
            j.addProperty("time", (Number)System.currentTimeMillis());
            j.addProperty("fps", (Number)mc.getFps());
            if (mc.getCurrentServer() != null) {
                j.addProperty("server", mc.getCurrentServer().ip);
            } else if (mc.getSingleplayerServer() != null) {
                try {
                    j.addProperty("world", mc.getSingleplayerServer().getWorldData().getLevelName());
                    j.addProperty("seed", (Number)mc.getSingleplayerServer().overworld().getSeed());
                }
                catch (Exception exception) {
                    // empty catch block
                }
            }
            if (mc.player != null && mc.level != null) {
                BlockPos pos = mc.player.blockPosition();
                j.addProperty("x", (Number)pos.getX());
                j.addProperty("y", (Number)pos.getY());
                j.addProperty("z", (Number)pos.getZ());
                j.addProperty("dimension", mc.level.dimension().identifier().toString());
                j.addProperty("day", (Number)(mc.level.getOverworldClockTime() / 24000L));
                try {
                    Holder b = mc.level.getBiome(pos);
                    String biome = b.unwrapKey().map(ResourceKey::identifier).map(Object::toString).orElse("unknown");
                    j.addProperty("biome", biome);
                }
                catch (Exception b) {
                    // empty catch block
                }
            }
            File meta = new File(png.getParentFile(), png.getName() + ".json");
            try (FileWriter w = new FileWriter(meta);){
                new Gson().toJson((JsonElement)j, (Appendable)w);
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
    }
}

