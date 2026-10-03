package com.thelads.core.v26_2.feature;

import com.google.gson.JsonElement;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.modules.ZoomModule;
import com.thelads.core.modules.ZoomTrace;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.KeyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (auto-world, ".lads-qa-capture-zoom" from the harness's LADS_VERIFY_CAPTURE_ZOOM): Lads Zoom through the game's own
 * key and scroll handlers. Saves zoom-1-off, zoom-2-in (4x), zoom-3-scroll (3 notches further in) and zoom-4-out, writes the
 * world FOV of every frame to screenshots/zoom-fov.csv (checked by ZoomTrace), checks the scroll left the hotbar alone and, with
 * Essential loaded, that Essential's zoom key does nothing while Lads Zoom is on (and does zoom with it off). All restored.
 */
final class ZoomCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final long PHASE = 1_100_000_000L; // Smooth Zoom lands exactly within 0.8 s
    private static final String[] SHOTS = {"zoom-1-off", "zoom-2-in", "zoom-3-scroll", "zoom-4-out", "zoom-5-essential-key", "zoom-6-essential-alone"};
    private static final ZoomTrace TRACE = new ZoomTrace();
    private static final List<String> FAILURES = new ArrayList<>();
    private static final Map<Option, JsonElement> OPTIONS = new LinkedHashMap<>();
    private static int step = -1, saved, slot;
    private static long due, modifiedBefore;
    private static boolean capturing, enabledBefore;
    private static float base;
    private static Object essential;
    private ZoomCapture() {}

    static boolean busy() { return step >= 0 && step < SHOTS.length; }

    /** Each client tick of the auto-world run: starts once the world is ready and the request exists. */
    static void tick(Path game, boolean ready) {
        if (step >= 0 || !ready) return;
        Path request = game.resolve(".lads-qa-capture-zoom");
        if (!Files.isRegularFile(request, LinkOption.NOFOLLOW_LINKS)) return;
        try { Files.delete(request); } catch (Exception failure) { LOGGER.error("Lads zoom capture FAILED: request", failure); return; }
        ZoomModule zoom = zoom();
        enabledBefore = zoom.isEnabled();
        modifiedBefore = zoom.getLastModified();
        for (Option option : zoom.getOptions()) OPTIONS.put(option, option.save().deepCopy());
        zoom.getOptions().forEach(Option::reset); // Hold, Smooth Zoom, Scroll to Zoom, Hand Zoom
        zoom.setEnabled(true);
        NativeWorldVerification.syntheticInput(true);
        slot = Minecraft.getInstance().player.getInventory().getSelectedSlot();
        LOGGER.info("Lads zoom capture BEGIN: {} frames through KeyboardHandler.keyPress and MouseHandler.onScroll", SHOTS.length);
        step = 0;
        due = System.nanoTime() + PHASE;
    }

    /** Each completed game frame (NativeWorldVerification.renderedFrame). */
    static void frame(RenderTarget target, Path game) {
        if (!busy() || capturing) return;
        TRACE.frame(NativeFeatures.lastWorldFov, NativeFeatures.lastWorldNanos);
        if (System.nanoTime() < due) return;
        if (step >= 4 && essential == null) { finish(game); return; }
        capturing = true;
        String name = SHOTS[step];
        try {
            Path folder = game.resolve("screenshots");
            Files.createDirectories(folder);
            if (!folder.toRealPath().startsWith(game)) throw new java.io.IOException("QA screenshot folder is outside the isolated game directory");
            Path output = folder.resolve(name + ".png");
            float fov = NativeFeatures.lastWorldFov;
            net.minecraft.client.Screenshot.takeScreenshot(target, image -> {
                try { image.writeToFile(output); saved++; LOGGER.info("Lads zoom frame {} (FOV {})", output, fov); }
                catch (Exception failure) { fail(name + ": " + failure); }
                finally { image.close(); Minecraft.getInstance().execute(() -> next(game)); }
            });
        } catch (Exception failure) {
            fail(name + ": " + failure);
            next(game);
        }
    }

    /** The shot of this step is saved: give the next input. */
    private static void next(Path game) {
        capturing = false;
        Minecraft mc = Minecraft.getInstance();
        float fov = NativeFeatures.lastWorldFov;
        try {
            switch (step) {
                case 0 -> {
                    base = fov;
                    key(true);
                    TRACE.phase("zoom-in", fov, base * ZoomModule.DEFAULT_ZOOM);
                }
                case 1 -> {
                    double notches = mc.options.mouseWheelSensitivity().get();
                    Method scroll = MouseHandler.class.getDeclaredMethod("onScroll", long.class, double.class, double.class);
                    scroll.setAccessible(true);
                    for (int i = 0; i < 3; i++) scroll.invoke(mc.mouseHandler, mc.getWindow().handle(), 0.0, 1.0);
                    check(mc.player.getInventory().getSelectedSlot() == slot, "scrolling while zoomed leaves the hotbar slot alone");
                    TRACE.phase("scroll-in", fov, (float) (base * ZoomModule.DEFAULT_ZOOM * Math.pow(1.25, -3 * notches)));
                }
                case 2 -> {
                    key(false);
                    TRACE.phase("zoom-out", fov, base);
                }
                case 3 -> {
                    TRACE.finish();
                    essential = essentialZoom();
                    if (essential == null) LOGGER.info("Lads zoom capture: Essential is not loaded; its zoom check is skipped");
                    else essentialKey().setDown(true);
                }
                case 4 -> {
                    check(Math.abs(fov - base) < 1e-4 && !essentialActive(), "Essential's zoom key does nothing while Lads Zoom is on (FOV " + fov + " of " + base + ")");
                    zoom().setEnabled(false); // the control: Essential's own zoom still works when Lads Zoom is off
                }
                case 5 -> {
                    check(fov < base * 0.5f, "with Lads Zoom off, Essential's zoom key zooms (FOV " + fov + " of " + base + "): the check above is real");
                    finish(game);
                    return;
                }
                default -> { }
            }
        } catch (Exception failure) {
            fail(SHOTS[step] + ": " + failure);
        }
        step++;
        due = System.nanoTime() + PHASE;
    }

    private static void finish(Path game) {
        Minecraft mc = Minecraft.getInstance();
        if (step <= 3) TRACE.finish();
        try {
            if (essential != null) essentialKey().setDown(false);
            key(false);
        } catch (Exception failure) { fail("release: " + failure); }
        ZoomModule zoom = zoom();
        OPTIONS.forEach(Option::load);
        zoom.setEnabled(enabledBefore);
        zoom.setLastModified(modifiedBefore);
        NativeWorldVerification.syntheticInput(false);
        FAILURES.addAll(TRACE.failures());
        try { Files.writeString(game.resolve("screenshots").resolve("zoom-fov.csv"), TRACE.csv(), StandardCharsets.UTF_8); }
        catch (Exception failure) { FAILURES.add("zoom-fov.csv: " + failure); }
        step = SHOTS.length;
        if (FAILURES.isEmpty()) LOGGER.info("Lads zoom capture END: {} frames saved, 0 failed; {}", saved, TRACE.summary());
        else LOGGER.error("Lads zoom capture FAILED: {} | {}", String.join(" | ", FAILURES), TRACE.summary());
    }

    private static void key(boolean down) {
        Minecraft mc = Minecraft.getInstance();
        int key = InputConstants.getKey(NativeKeyBindings.ZOOM.saveString()).getValue();
        try {
            Method press = KeyboardHandler.class.getDeclaredMethod("keyPress", long.class, int.class, KeyEvent.class);
            press.setAccessible(true);
            press.invoke(mc.keyboardHandler, mc.getWindow().handle(), down ? InputConstants.PRESS : InputConstants.RELEASE,
                new KeyEvent(key, 0, 0)); // 26.3 (SDL): the physical key and no character
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    private static Object essentialZoom() {
        try {
            Class<?> handler = Class.forName("gg.essential.handlers.ZoomHandler", true, ZoomCapture.class.getClassLoader());
            return handler.getMethod("getInstance").invoke(null);
        } catch (ReflectiveOperationException | LinkageError absent) { return null; }
    }
    private static KeyMapping essentialKey() throws ReflectiveOperationException {
        return (KeyMapping) essential.getClass().getField("zoomKeybinding").get(essential);
    }
    private static boolean essentialActive() throws ReflectiveOperationException {
        return essential.getClass().getField("isZoomActive").getBoolean(essential);
    }

    private static ZoomModule zoom() { return (ZoomModule) ModuleManager.getInstance().getModule("Zoom"); }
    private static void check(boolean result, String description) {
        if (result) LOGGER.info("Lads zoom capture PASS: {}", description);
        else fail(description);
    }
    private static void fail(String failure) {
        FAILURES.add(failure);
        LOGGER.error("Lads zoom capture check failed: {}", failure);
    }
}
