package com.thelads.core.v26_2.input;

import com.thelads.core.perf.InputLatencyQa;
import java.util.concurrent.locks.LockSupport;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLEvents;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.sdl.SDL_Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (auto-world run with LADS_VERIFY_INPUT=1): the input-latency pacing's in-game evidence (InputLatencyQa) once the QA
 * world has run 20 s, plus an 8000 Hz synthetic mouse: SDL_EVENT_MOUSE_MOTION events pushed into SDL's queue from a thread, as
 * SDL's own raw input thread does, so the game's remaining per-event cost on 26.x shows as frame rate.
 */
public final class InputProbe26 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final int MOUSE_HZ = 8000;
    private static InputLatencyQa qa;
    private static long inWorldSince;
    private static volatile boolean flooding;
    private static boolean failed;

    private InputProbe26() {}

    public static void register() {
        if (!"1".equals(System.getenv("LADS_VERIFY_INPUT")) || !Boolean.getBoolean("thelads.verifyAutoWorld")) return;
        ClientTickEvents.END_CLIENT_TICK.register(InputProbe26::tick);
    }

    private static void tick(Minecraft mc) {
        if (failed || qa != null && qa.done()) return;
        if (mc.level == null || mc.player == null) { inWorldSince = 0; return; }
        long now = System.nanoTime();
        if (inWorldSince == 0) inWorldSince = now;
        if (now - inWorldSince < 20_000_000_000L) return;
        try {
            if (qa == null) {
                qa = new InputLatencyQa(new InputLatencyQa.Target() {
                    @Override public void paced(boolean on) { InputLatency26.qaPaused = !on; }
                    @Override public void gpuLoad(int units) { InputLatency26.qaCopies = units; }
                    @Override public void flood(int mode) { setFlood(mode == 1); }
                    @Override public void window(String action) { windowAction(mc, action); }
                    @Override public void log(String line) { LOGGER.info(line); }
                }, InputLatency26.meter(), InputLatency26.PACER, 1, 1);
                LOGGER.info("Lads input probe: 26.3, device {}, limit {}, vsync {}, pacing {}", com.mojang.blaze3d.systems.RenderSystem.getDevice().getDeviceInfo(),
                    mc.options.framerateLimit().get(), mc.options.enableVsync().get(), InputLatency26.reflex);
            }
            qa.tick();
        } catch (Throwable t) {
            setFlood(false);
            InputLatency26.qaCopies = 0;
            InputLatency26.qaPaused = false;
            failed = true;
            LOGGER.error("Lads input probe FAILED", t);
        }
    }

    private static int savedWidth, savedHeight;

    /** Resize and restore the window, and F11's fullscreen option on and off (Lads makes it borderless with BorderlessFullscreen). */
    private static void windowAction(Minecraft mc, String action) {
        var window = mc.getWindow();
        LOGGER.info("Lads input probe: window {} (was {}x{}, fullscreen option {})", action, window.getScreenWidth(), window.getScreenHeight(), mc.options.fullscreen().get());
        switch (action) {
            case "resize" -> {
                savedWidth = window.getScreenWidth();
                savedHeight = window.getScreenHeight();
                SDLVideo.SDL_SetWindowSize(window.handle(), 800, 480);
            }
            case "restore" -> SDLVideo.SDL_SetWindowSize(window.handle(), savedWidth, savedHeight);
            default -> mc.options.fullscreen().set("fullscreen".equals(action));
        }
    }

    private static void setFlood(boolean on) {
        if (on == flooding) return;
        flooding = on;
        if (!on) return;
        int window = SDLVideo.SDL_GetWindowID(Minecraft.getInstance().getWindow().handle());
        Thread flood = new Thread(() -> {
            try (SDL_Event event = SDL_Event.calloc()) {
                event.motion().type(SDLEvents.SDL_EVENT_MOUSE_MOTION).windowID(window).x(100).y(100).xrel(1).yrel(0);
                long next = System.nanoTime(), started = next, pushed = 0;
                while (flooding) {
                    long now = System.nanoTime();
                    for (; next <= now; next += 1_000_000_000L / MOUSE_HZ) {
                        event.motion().timestamp(0);
                        SDLEvents.SDL_PushEvent(event);
                        pushed++;
                    }
                    LockSupport.parkNanos(200_000L);
                }
                LOGGER.info("Lads input probe: synthetic mouse pushed {} SDL motion events ({} per second)", pushed, Math.round(pushed * 1e9 / (System.nanoTime() - started)));
            }
        }, "Lads QA synthetic mouse");
        flood.setDaemon(true);
        flood.start();
    }
}
