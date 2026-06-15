package com.thelads.core.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

import java.io.File;
import java.io.FileWriter;

/**
 * Writes a small JSON sidecar next to a screenshot capturing where/when it was
 * taken (server/world, coords, dimension, biome, in-world day, fps). The
 * launcher's Gallery can read these and sort by them.
 */
public class ScreenshotMeta {
    public static void write(File png) {
        try {
            Minecraft mc = Minecraft.getInstance();
            JsonObject j = new JsonObject();
            j.addProperty("time", System.currentTimeMillis());
            j.addProperty("fps", mc.getFps());

            if (mc.getCurrentServer() != null) {
                j.addProperty("server", mc.getCurrentServer().ip);
            } else if (mc.getSingleplayerServer() != null) {
                try {
                    j.addProperty("world", mc.getSingleplayerServer().getWorldData().getLevelName());
                    j.addProperty("seed", mc.getSingleplayerServer().overworld().getSeed());
                } catch (Exception ignored) {
                }
            }

            if (mc.player != null && mc.level != null) {
                BlockPos pos = mc.player.blockPosition();
                j.addProperty("x", pos.getX());
                j.addProperty("y", pos.getY());
                j.addProperty("z", pos.getZ());
                j.addProperty("dimension", mc.level.dimension().identifier().toString());
                j.addProperty("day", mc.level.getOverworldClockTime() / 24000L);
                try {
                    Holder<Biome> b = mc.level.getBiome(pos);
                    String biome = b.unwrapKey().map(ResourceKey::identifier).map(Object::toString).orElse("unknown");
                    j.addProperty("biome", biome);
                } catch (Exception ignored) {
                }
            }

            File meta = new File(png.getParentFile(), png.getName() + ".json");
            try (FileWriter w = new FileWriter(meta)) {
                new Gson().toJson(j, w);
            }
        } catch (Exception ignored) {
        }
    }
}
