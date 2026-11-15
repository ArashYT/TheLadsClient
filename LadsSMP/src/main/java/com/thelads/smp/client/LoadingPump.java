package com.thelads.smp.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps the window painting while the render thread is busy with one long step of loading (a mod's resource-reload task, JEI starting,
 * the creative tabs being built, a world being created). Minecraft only presents a frame between two such steps, so a 10 s step is a
 * 10 s frozen picture. The slow steps call {@link #poll()} at safe points (between tasks, between JEI stages, per model part); if the last
 * frame is older than 120 ms and only a loading screen is up, it renders one normal frame right there, exactly what the main loop would
 * draw next. Any failure switches the pump off for the rest of the session. -Dladssmp.pump=false turns it off.
 */
@Environment(EnvType.CLIENT)
public final class LoadingPump {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP/LoadingPump");
    private static final boolean ON = !"false".equalsIgnoreCase(System.getProperty("ladssmp.pump", "true"));
    private static final long GAP_NS = Long.getLong("ladssmp.pump.gapms", 120L) * 1_000_000L;
    private static volatile long lastFrame = System.nanoTime();
    private static int frameDepth;
    private static boolean inPump, broken, ready;
    private static long pumps;

    private LoadingPump() { }

    /** A frame (real or pumped) is starting. */
    public static void frameStart() { frameDepth++; }

    /** A frame was presented. */
    public static void frameEnd() {
        ready = true; // the constructor's first frame is done: the renderer, the frame limiter and the overlay exist
        if (frameDepth > 0) frameDepth--;
        if (!inPump) packetDepth = 0; // a normal frame is never inside packet handling
        lastFrame = System.nanoTime();
    }

    public static long pumpedFrames() { return pumps; }

    private static int counter, packetDepth, blockDepth;
    private static volatile long joinWindowUntil;

    /** The login packet arrived: for 90 s, slow steps inside packet handling (JEI, creative tabs) may pump too. */
    public static void onLogin() { joinWindowUntil = System.nanoTime() + 90_000_000_000L; }

    /** Minecraft#managedBlock (the client thread waits for a future, e.g. the data packs of a new world) starts/ends. */
    public static void blockStart() { blockDepth++; }

    public static void blockEnd() { if (blockDepth > 0) blockDepth--; }

    public static void packetsStart() { packetDepth++; }

    public static void packetsEnd() { if (packetDepth > 0) packetDepth--; }

    /** For hot paths called millions of times (identifier creation, registry and block-state lookups): looks at the clock every 256th call only. */
    public static void pollFast() {
        if ((++counter & 0xFF) != 0) return;
        poll();
    }

    /** A frame that threw after acquiring the window's swapchain image would leave it acquired and Minecraft#renderFrame would skip every later frame: present it. */
    private static void releaseSurface(Minecraft mc) {
        try {
            java.lang.reflect.Field f = Minecraft.class.getDeclaredField("windowSurface");
            f.setAccessible(true);
            Object surface = f.get(mc);
            if ((Boolean) surface.getClass().getMethod("isAcquired").invoke(surface)) surface.getClass().getMethod("present").invoke(surface);
        } catch (Throwable ignored) { }
    }

    /** Call from a safe point on the render thread. Cheap when no frame is due. */
    public static void poll() {
        if (!ON || !ready || broken || inPump || frameDepth > 0) return;
        long now = System.nanoTime();
        if (now - lastFrame < GAP_NS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gui == null || !mc.isSameThread()) return;
        Screen s = mc.gui.screen();
        boolean loading = mc.gui.overlay() instanceof LoadingOverlay || s instanceof LevelLoadingScreen || s instanceof GenericMessageScreen || s instanceof CreateWorldScreen
            || (packetDepth > 0 && mc.level != null && now < joinWindowUntil)
            || (blockDepth > 0 && mc.level == null);
        if (!loading) return;
        inPump = true;
        try {
            mc.renderFrame(false);
            pumps++;
        } catch (Throwable t) {
            broken = true;
            LOGGER.error("Loading screen pump failed, switched off for this session", t);
            releaseSurface(mc);
        } finally {
            inPump = false;
            frameDepth = 0;
            lastFrame = System.nanoTime();
        }
    }
}
