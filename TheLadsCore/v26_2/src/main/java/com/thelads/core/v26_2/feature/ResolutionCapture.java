package com.thelads.core.v26_2.feature;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.TimerQuery;
import com.thelads.core.client.RenderScalePolicy;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (auto-world, ".lads-qa-capture-resolution" from the harness's LADS_VERIFY_CAPTURE_RESOLUTION): Better Resolution
 * photographed at native, 50 % with each Algorithm, the Balanced preset and 200 %, each logged with its FPS (QA caps it at 120),
 * the GPU time of whole frames (a TimerQuery from NativeRenderScale.beginFrame to the completed frame) and the world target's
 * size. The stages replace the module's settings for the frames they run, so nothing is changed or saved; Autohide is off
 * meanwhile so every photo has the HUD, and the player flies (no sinking in the QA world's water) so every photo has the same view;
 * both are put back after.
 */
final class ResolutionCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final String[] NAMES = {"native", "50-linear", "50-nearest", "50-smooth", "50-sharp", "balanced-smooth", "200-smooth"};
    private static final RenderScalePolicy.Settings[] STAGES = {
        stage(0, 100, RenderScalePolicy.LINEAR), stage(0, 50, RenderScalePolicy.LINEAR), stage(0, 50, RenderScalePolicy.NEAREST),
        stage(0, 50, RenderScalePolicy.SMOOTH), stage(0, 50, RenderScalePolicy.SHARP), stage(2, 100, RenderScalePolicy.SMOOTH),
        stage(0, 200, RenderScalePolicy.SMOOTH)};
    private static final long SETTLE = 1_000_000_000L, STAGE = 3_000_000_000L;
    private static int step = -1, frames, gpuSamples, failures;
    private static long stageStart, gpuNanos;
    private static boolean capturing, autohide, flying;
    private static long autohideModified;
    private static TimerQuery timer;
    private static NativeRenderScale owner;
    private ResolutionCapture() {}

    private static RenderScalePolicy.Settings stage(int preset, double scale, int method) {
        return new RenderScalePolicy.Settings(true, preset, scale, method, false, 0, 50);
    }

    static boolean busy() { return step >= 0 && step < NAMES.length; }

    /** Each client tick of the auto-world run: starts once the world is ready and the request exists. */
    static void tick(Path game, boolean ready) {
        if (step >= 0 || !ready) return;
        Path request = game.resolve(".lads-qa-capture-resolution");
        if (!Files.isRegularFile(request, LinkOption.NOFOLLOW_LINKS)) return;
        try { Files.delete(request); } catch (Exception failure) { LOGGER.error("Lads resolution capture FAILED: request", failure); return; }
        boolean shaders = FabricLoader.getInstance().isModLoaded("iris") && net.irisshaders.iris.api.v0.IrisApi.getInstance().isShaderPackInUse();
        LOGGER.info("Lads resolution capture BEGIN: {} stages, Iris shader pack {}", NAMES.length, shaders ? "in use" : "off");
        Module hide = ModuleManager.getInstance().getModule("Autohide");
        autohide = hide.isEnabled();
        autohideModified = hide.getLastModified();
        hide.setEnabled(false);
        flying = Minecraft.getInstance().player.getAbilities().flying;
        Minecraft.getInstance().player.getAbilities().flying = true;
        timer = new TimerQuery();
        start(0);
    }

    /** NativeRenderScale.beginFrame: the stage's settings for this frame; the GPU timer starts. */
    static RenderScalePolicy.Settings settings(NativeRenderScale scale, RenderScalePolicy.Settings actual) {
        if (!busy()) return actual;
        owner = scale;
        if (timer.getStatus() == TimerQuery.Status.NOT_RECORDING) {
            // get() averages the last three timed frames: after SETTLE they are all this stage's.
            if (System.nanoTime() - stageStart > SETTLE) { gpuNanos += timer.get(); gpuSamples++; }
            timer.beginProfile();
        }
        return STAGES[step];
    }

    /** Each completed game frame (NativeWorldVerification.renderedFrame): counted, and photographed when the stage ends. */
    static void frame(RenderTarget target, Path game) {
        if (!busy()) return;
        if (timer.getStatus() == TimerQuery.Status.STARTED) timer.endProfile();
        long now = System.nanoTime();
        if (now - stageStart > SETTLE) frames++;
        if (capturing || now - stageStart < STAGE) return;
        capturing = true;
        String name = NAMES[step];
        RenderTarget world = owner == null ? null : owner.worldTarget();
        double fps = frames / ((now - stageStart - SETTLE) / 1e9);
        double gpu = gpuSamples == 0 ? 0 : gpuNanos / 1e6 / gpuSamples;
        LOGGER.info("Lads resolution stage {}: {} FPS, GPU {} ms per frame, world {}, window {}x{}", name, String.format("%.1f", fps),
            String.format("%.3f", gpu), world == null || !owner.scaledThisFrame() ? "native" : world.width + "x" + world.height, target.width, target.height);
        try {
            Path folder = game.resolve("screenshots");
            Files.createDirectories(folder);
            if (!folder.toRealPath().startsWith(game)) throw new java.io.IOException("QA screenshot folder is outside the isolated game directory");
            Path output = folder.resolve("resolution-" + name + ".png");
            net.minecraft.client.Screenshot.takeScreenshot(target, image -> {
                try { image.writeToFile(output); }
                catch (Exception failure) { failures++; LOGGER.error("Lads resolution capture check failed: {}", name, failure); }
                finally { image.close(); Minecraft.getInstance().execute(ResolutionCapture::next); }
            });
        } catch (Exception failure) {
            failures++;
            LOGGER.error("Lads resolution capture check failed: {}", name, failure);
            next();
        }
    }

    private static void next() {
        capturing = false;
        if (step + 1 < NAMES.length) { start(step + 1); return; }
        step = NAMES.length;
        if (timer.getStatus() == TimerQuery.Status.STARTED) timer.endProfile();
        timer.close();
        Module hide = ModuleManager.getInstance().getModule("Autohide");
        hide.setEnabled(autohide);
        hide.setLastModified(autohideModified);
        if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.getAbilities().flying = flying;
        if (failures == 0) LOGGER.info("Lads resolution capture END: {} stages saved as screenshots/resolution-*.png, 0 failed", NAMES.length);
        else LOGGER.error("Lads resolution capture FAILED: {} of {} stages", failures, NAMES.length);
    }

    private static void start(int index) {
        step = index;
        stageStart = System.nanoTime();
        frames = 0;
        gpuSamples = 0;
        gpuNanos = 0;
    }
}
