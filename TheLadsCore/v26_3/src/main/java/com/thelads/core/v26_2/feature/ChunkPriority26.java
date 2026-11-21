package com.thelads.core.v26_2.feature;

import com.thelads.core.perf.ChunkPriority;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Smart Chunk Priority, send order (b1): while the integrated server picks the next chunks to send to a player, the order is
 * biased toward where the player looks (the nearest ring stays in vanilla order). Server-to-client order only, nothing a
 * remote server or the client sends changes; remote servers never run this code (the integrated server is the only one here).
 */
public final class ChunkPriority26 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final boolean OTHER_ENGINE = FabricLoader.getInstance().isModLoaded("c2me");
    /** Sodium family replaces the section builder; this queue is then unused. */
    private static final boolean MESH_ENGINE = FabricLoader.getInstance().isModLoaded("sodium") || FabricLoader.getInstance().isModLoaded("embeddium");
    private static boolean logged;
    private static float yaw;
    private static boolean active;

    private ChunkPriority26() {}

    /** Server thread, start of PlayerChunkSender.sendNextChunks. */
    public static void begin(ServerPlayer player) {
        active = false;
        if (OTHER_ENGINE || !ChunkPriority.on() || !(player.level().getServer() instanceof IntegratedServer)) return;
        yaw = player.getYRot();
        active = true;
        if (!logged) {
            logged = true;
            LOGGER.info("[Lads] Smart Chunk Priority: singleplayer/LAN chunk send order follows the look direction (-Dlads.chunkPriority=false to disable)");
        }
    }

    public static void end() {
        active = false;
    }

    public static boolean active() {
        return active;
    }

    public static int key(int dx, int dz) {
        return ChunkPriority.key(dx, dz, yaw);
    }

    private static volatile boolean meshActive;
    private static volatile float meshYaw;
    private static boolean meshLogged;

    /** Main thread, every time the section builder gets the camera: look direction for the mesh-build order (a). */
    public static void meshCamera() {
        Minecraft mc = Minecraft.getInstance();
        boolean on = !OTHER_ENGINE && !MESH_ENGINE && ChunkPriority.on() && mc.hasSingleplayerServer() && mc.player != null;
        meshActive = on;
        if (!on) return;
        meshYaw = mc.player.getYRot();
        if (!meshLogged) {
            meshLogged = true;
            LOGGER.info("[Lads] Smart Chunk Priority: singleplayer/LAN section meshes build in look-direction order");
        }
    }

    /** Worker thread (section build queue pick): the distance the queue compares for a section. */
    public static double meshDistance(double distSq, double dx, double dz) {
        return meshActive ? ChunkPriority.meshDistance(distSq, dx, dz, meshYaw) : distSq;
    }
}
