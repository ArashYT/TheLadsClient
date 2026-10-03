package com.thelads.core.v1_8_9.feature;

import static com.thelads.core.v1_8_9.feature.CoreProbe.after;
import static com.thelads.core.v1_8_9.feature.CoreProbe.check;
import static com.thelads.core.v1_8_9.feature.CoreProbe.screenshot;

import com.google.gson.JsonElement;
import com.thelads.core.config.Option;
import com.thelads.core.modules.ZoomModule;
import com.thelads.core.modules.ZoomTrace;
import com.thelads.core.v1_8_9.mixin.EntityRendererAccessor;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.input.Keyboard;

/**
 * QA only: Lads Zoom (Zoom189), run by CoreProbe in its QA world after Probe151. The zoom key held and released and three wheel
 * notches, all through runTick's own input loop; every frame's world FOV must follow Smooth Zoom's curve (ZoomTrace, written to
 * lads-qa/screenshots/160-zoom-fov.csv), the wheel must leave the hotbar alone, and OptiFine's zoom key, held in LWJGL's key state,
 * must not zoom while Lads Zoom is on (and must with it off: OptiFine intact). Module and options are put back as found.
 * Screenshots: 160-zoom-1-off, -2-in, -3-scroll, -4-out.
 */
final class Probe160 {
    static final List<CoreProbe.Step> STEPS = Arrays.<CoreProbe.Step>asList(Probe160::start, Probe160::off, Probe160::in,
        Probe160::scrolled, Probe160::out, Probe160::optiFine, Probe160::restore);
    private static final int TICKS = 24; // 1.2 s: Smooth Zoom lands exactly within 0.8 s
    private static final Map<Option, JsonElement> optionsWere = new LinkedHashMap<>();
    private static ZoomTrace trace;
    private static boolean wasEnabled, started;
    private static long modifiedWas;
    private static int slot;
    private static float base;

    private Probe160() {}

    private static boolean start(Minecraft mc) {
        ZoomModule zoom = Zoom189.zoom();
        wasEnabled = zoom.isEnabled();
        modifiedWas = zoom.getLastModified();
        for (Option option : zoom.getOptions()) optionsWere.put(option, option.save());
        zoom.getOptions().forEach(Option::reset); // Hold, Smooth Zoom, Scroll to Zoom, Hand Zoom
        zoom.setEnabled(true);
        started = true;
        trace = new ZoomTrace();
        Zoom189.trace = trace;
        Zoom189.synthetic = true;
        slot = mc.thePlayer.inventory.currentItem;
        return after(30);
    }

    private static boolean off(Minecraft mc) throws Exception {
        base = Zoom189.lastWorldFov;
        check(!Zoom189.zoom().isActive() && base > 1, "Zoom: unzoomed, the world FOV is Minecraft's own (" + base + ")");
        screenshot(mc, "160-zoom-1-off");
        CoreProbe.key(Zoom189.ZOOM.getKeyCode(), 'c', true);
        trace.phase("zoom-in", base, base * ZoomModule.DEFAULT_ZOOM);
        return after(TICKS);
    }

    private static boolean in(Minecraft mc) throws Exception {
        check(Zoom189.zoom().isActive() && Math.abs(Zoom189.lastWorldFov - base * ZoomModule.DEFAULT_ZOOM) < 1e-3,
            "Zoom: the held zoom key (" + Keyboard.getKeyName(Zoom189.ZOOM.getKeyCode()) + " through runTick) zooms to 4x (FOV " + Zoom189.lastWorldFov + ")");
        screenshot(mc, "160-zoom-2-in");
        float before = Zoom189.lastWorldFov;
        for (int i = 0; i < 3; i++) CoreProbe.wheel(120);
        trace.phase("scroll-in", before, base * (float) (ZoomModule.DEFAULT_ZOOM * Math.pow(1.25, -3)));
        return after(TICKS);
    }

    private static boolean scrolled(Minecraft mc) throws Exception {
        check(mc.thePlayer.inventory.currentItem == slot, "Zoom: three wheel notches while zoomed leave the hotbar slot alone");
        screenshot(mc, "160-zoom-3-scroll");
        float before = Zoom189.lastWorldFov;
        CoreProbe.key(Zoom189.ZOOM.getKeyCode(), 'c', false);
        trace.phase("zoom-out", before, base);
        return after(TICKS);
    }

    private static boolean out(Minecraft mc) throws Exception {
        screenshot(mc, "160-zoom-4-out");
        trace.finish();
        Zoom189.trace = null;
        File csv = new File(mc.mcDataDir, "lads-qa/screenshots/160-zoom-fov.csv");
        Files.write(csv.toPath(), trace.csv().getBytes(StandardCharsets.UTF_8));
        LogManager.getLogger("TheLadsCore").info("Lads 1.8.9 zoom trace: {} ({})", trace.summary(), csv);
        check(trace.failures().isEmpty(), "Zoom: every frame's FOV followed Smooth Zoom's curve, never overshot and landed exactly: "
            + (trace.failures().isEmpty() ? trace.summary() : trace.failures()));
        check(!Zoom189.zoom().isActive() && Zoom189.lastWorldFov == base, "Zoom: released, the FOV is exactly Minecraft's again (" + Zoom189.lastWorldFov + ")");
        return after(2);
    }

    /** OptiFine reads its zoom key from LWJGL's key state (GameSettings.isKeyDown): QA sets that state for C directly. */
    private static boolean optiFine(Minecraft mc) throws Exception {
        KeyBinding optiFine = null;
        for (KeyBinding binding : mc.gameSettings.keyBindings) if ("of.key.zoom".equals(binding.getKeyDescription())) optiFine = binding;
        if (optiFine == null || optiFine.getKeyCode() <= 0) {
            LogManager.getLogger("TheLadsCore").info("Lads 1.8.9 zoom: OptiFine's zoom key is not loaded or not on a key; its check is skipped");
            return after(1);
        }
        int code = optiFine.getKeyCode();
        Field field = Keyboard.class.getDeclaredField("keyDownBuffer");
        field.setAccessible(true);
        ByteBuffer keys = (ByteBuffer) field.get(null);
        EntityRendererAccessor renderer = (EntityRendererAccessor) mc.entityRenderer;
        boolean smoothWas = mc.gameSettings.smoothCamera;
        ZoomModule zoom = Zoom189.zoom();
        try {
            keys.put(code, (byte) 1);
            check(Keyboard.isKeyDown(code) && !GameSettings.isKeyDown(optiFine),
                "Zoom: OptiFine's zoom key, held in LWJGL's key state, reads as up while Lads Zoom is on");
            float held = renderer.ladsFov(1, true);
            check(Math.abs(held - base) < 1e-3 && mc.gameSettings.smoothCamera == smoothWas,
                "Zoom: with OptiFine's zoom key held, OptiFine leaves the FOV (" + held + " of " + base + ") and smooth camera alone");
            zoom.setEnabled(false);
            float alone = renderer.ladsFov(1, true);
            check(Math.abs(alone - base / 4) < 1e-2, "Zoom: with Lads Zoom off, OptiFine's own zoom still works (" + alone + " of " + base
                + "): OptiFine is intact and the check above is real");
        } finally {
            keys.put(code, (byte) 0);
            renderer.ladsFov(1, true); // OptiFine leaves its zoom mode and puts smooth camera back
            zoom.setEnabled(true);
        }
        check(mc.gameSettings.smoothCamera == smoothWas, "Zoom: OptiFine put smooth camera back (" + mc.gameSettings.smoothCamera + ")");
        return after(2);
    }

    private static boolean restore(Minecraft mc) {
        stop();
        check(Zoom189.zoom().isEnabled() == wasEnabled && !Zoom189.zoom().isActive(), "Zoom: module and options are back as found");
        return after(1);
    }

    /** Also CoreProbe.finish after a failure: the key up, the module and options back. */
    static void stop() {
        if (!started) return;
        started = false;
        try { CoreProbe.key(Zoom189.ZOOM.getKeyCode(), 'c', false); } catch (Throwable ignored) {}
        Zoom189.trace = null;
        Zoom189.synthetic = false;
        ZoomModule zoom = Zoom189.zoom();
        zoom.release();
        optionsWere.forEach(Option::load);
        zoom.setEnabled(wasEnabled);
        zoom.setLastModified(modifiedWas);
    }
}
