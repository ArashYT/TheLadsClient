package com.thelads.core.v26_2.feature;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.thelads.core.client.RenderScalePolicy;
import java.nio.file.Files;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import org.slf4j.LoggerFactory;

/** Isolated, opt-in QA of real world frames and GPU sampling. Never changes or saves module preferences. */
final class NativeRenderScaleProbe {
    private static final boolean REQUESTED = Boolean.getBoolean("thelads.verifyRenderScale");
    private static boolean checkedDirectory, done, active, gpuStarted;
    private static int stage, frames, passed, pendingReadbacks;
    private static long warmup, stageStart, worldFramesBefore;
    private static Throwable gpuFailure;
    private static RenderTarget priorWorld;
    private static RenderScalePolicy.Settings savedSettings;
    private NativeRenderScaleProbe() {}

    /** Requested and not finished: a capture that opens a screen (Chat Heads) waits, or this probe loses its world frames. */
    static boolean pending() { return REQUESTED && !done; }

    static RenderScalePolicy.Settings settings(NativeRenderScale owner, RenderScalePolicy.Settings actual, boolean world) {
        if (!REQUESTED || done) return actual;
        try {
            if (!checkedDirectory) {
                checkedDirectory = true;
                var game = FabricLoader.getInstance().getGameDir().toRealPath();
                var verification = game.getParent();
                require(game.getFileName().toString().equals("26.3-title") && verification != null
                    && verification.getFileName().toString().equals("verification")
                    && verification.getParent().getFileName().toString().equals("artifacts")
                    && Files.isRegularFile(verification.getParent().getParent().resolve("TheLadsCore/settings.gradle")),
                    "render-scale QA requires the isolated artifacts/verification/26.3-title directory");
                LoggerFactory.getLogger("TheLadsCore").info("Lads render scale probe BEGIN: GPU sampling and real world frames; preferences remain unchanged");
            }
            if (!world || !(NativeFeatures.interactive() || NativeWorldVerification.worldReady())) {
                // Other isolated probes open containers asynchronously. Restart this stage's
                // continuous-frame sample once the screen closes, without counting UI frames.
                if (active) { active = false; frames = 0; priorWorld = null; }

                warmup = 0;
                return actual;
            }
            long now = System.nanoTime();
            if (warmup == 0) warmup = now;
            if (now - warmup < 2_000_000_000L) return actual;
            if (!active) {
                active = true;
                stageStart = now;
                savedSettings = actual;
                worldFramesBefore = owner.completedWorldFrames();
            }
            require(savedSettings.equals(actual), "QA does not mutate declared settings");
            return switch (stage) {
                case 0 -> test(true, 50, RenderScalePolicy.LINEAR, false);
                case 1 -> test(true, 50, RenderScalePolicy.NEAREST, false);
                case 2 -> test(true, 150, RenderScalePolicy.SMOOTH, false);
                case 3 -> test(false, 50, RenderScalePolicy.LINEAR, false);
                case 4 -> test(true, 100, RenderScalePolicy.LINEAR, false);
                default -> test(true, 75, RenderScalePolicy.SHARP, true);
            };
        } catch (Throwable failure) {
            fail(failure);
            return actual;
        }
    }

    static void endFrame(NativeRenderScale owner, RenderTarget nativeTarget, boolean world, boolean succeeded) {
        if (!REQUESTED || done || !active) return;
        try {
            require(succeeded && world, "world frame completes successfully");
            var mc = Minecraft.getInstance();
            require(mc != null && mc.gameRenderer != null && mc.levelRenderer != null
                && mc.getWindow() != null && NativeRenderScale.ready(nativeTarget), "world renderer lifecycle is ready");
            var window = mc.gameRenderer.gameRenderState().windowRenderState;
            var outline = NativeRenderScale.persistentOutline();
            require(NativeRenderScale.ready(outline), "persistent outline attachment is available outside the frame graph");
            require(mc.gameRenderer.mainRenderTarget() == nativeTarget, "GUI/presentation uses the original target");
            require(nativeTarget.width == window.width && nativeTarget.height == window.height,
                "native GUI dimensions survive scaled world rendering");
            require(window.guiScale == mc.getWindow().getGuiScale(), "GUI scale is unchanged");
            var target = owner.worldTarget();
            if (stage <= 2 || stage == 5) {
                double scale = stage == 2 ? 1.5 : stage == 5 ? owner.effectiveScale() : .5;
                var size = RenderScalePolicy.size(nativeTarget.width, nativeTarget.height, scale,
                    RenderSystem.getDevice().getDeviceInfo().limits().maxTextureSize());
                require(owner.scaledThisFrame() && target != null && target.width == size.width() && target.height == size.height(),
                    "world render uses real scaled color/depth attachments");
                require(target.getDepthTexture().getWidth(0) == size.width() && target.getDepthTexture().getHeight(0) == size.height(),
                    "world depth matches its color attachment");
                require(outline.width == size.width() && outline.height == size.height(), "outline attachment follows world resolution");
                require(owner.completedWorldFrames() > worldFramesBefore, "world composite actually executes");
                if (stage == 5) require(scale >= .5 && scale <= .75, "dynamic scale stays within its bounds");
            } else {
                require(!owner.scaledThisFrame() && target == null, "disabled/native scale releases the extra GPU target");
                require(outline.width == nativeTarget.width && outline.height == nativeTarget.height, "native outline resolution restored");
            }
            if (stage == 0 && !gpuStarted) {
                gpuStarted = true;
                startGpuChecks();
            }
            if (gpuFailure != null) throw new IllegalStateException("GPU upscale pixel readback failed", gpuFailure);
            require(pendingReadbacks == 0 || System.nanoTime() - stageStart < 10_000_000_000L,
                "GPU upscale pixel readback completes within ten seconds");
            frames++;
            // Multiple real frames per setting catch stale/destroyed attachment reuse.
            if (frames < 12 || System.nanoTime() - stageStart < 1_500_000_000L || pendingReadbacks != 0) return;
            passed += stage <= 2 ? 10 : stage == 5 ? 11 : 8;
            LoggerFactory.getLogger("TheLadsCore").info("Lads render scale probe stage {}: {} world frames, world {}x{}, native {}x{}, scale {}",
                stage, frames, target == null ? nativeTarget.width : target.width, target == null ? nativeTarget.height : target.height,
                nativeTarget.width, nativeTarget.height, owner.effectiveScale());
            if (priorWorld != null && priorWorld != target) require(priorWorld.getColorTexture() == null, "old resize/disable attachment is destroyed");
            priorWorld = target;
            if (++stage >= 6) {
                done = true;
                active = false;
                LoggerFactory.getLogger("TheLadsCore").info("Lads render scale probe END: {} passed, 0 failed", passed);
            } else {
                frames = 0;
                stageStart = System.nanoTime();
                worldFramesBefore = owner.completedWorldFrames();
            }
        } catch (Throwable failure) { fail(failure); }
    }

    private static void startGpuChecks() {
        require(NativeRenderScale.upscaleReady(), "Better Resolution's Smooth and Sharp shaders compile");
        int[] middles = new int[4];
        TextureTarget source = new TextureTarget("Lads scale QA source", 2, 2, GpuFormat.RGBA8_UNORM, null);
        try (NativeImage pixels = new NativeImage(2, 2, false)) {
            for (int y = 0; y < 2; y++) {
                pixels.setPixel(0, y, 0xffff0000);
                pixels.setPixel(1, y, 0xff0000ff);
            }
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(source.getColorTexture(), pixels);
        }
        pendingReadbacks = 4;
        for (int method = 0; method < 4; method++) {
            boolean nearest = method == RenderScalePolicy.NEAREST;
            int index = method;
            TextureTarget destination = new TextureTarget("Lads scale QA destination", 8, 4, GpuFormat.RGBA8_UNORM, null);
            NativeRenderScale.blit(source, destination, method);
            Screenshot.takeScreenshot(destination, pixels -> {
                try (pixels) {
                    int left = pixels.getPixel(0, 1), right = pixels.getPixel(7, 1), middle = pixels.getPixel(3, 1);
                    require((left & 0x00ffffff) == 0x00ff0000 && (right & 0x00ffffff) == 0x000000ff,
                        "GPU composite (method " + index + ") covers the full destination with the expected colors");
                    if (nearest) require((middle & 0x00ffffff) == 0x00ff0000, "nearest sampling preserves an exact source pixel");
                    else require(((middle >>> 16) & 255) > 0 && (middle & 255) > 0,
                        "method " + index + " blends neighboring pixels on the GPU");
                    middles[index] = middle;
                    passed += 2;
                } catch (Throwable failure) { gpuFailure = failure; }
                finally {
                    destination.destroyBuffers();
                    if (--pendingReadbacks == 0) {
                        source.destroyBuffers();
                        // Sharp holds each world pixel flatter: next to the edge it keeps more of the red pixel than Linear.
                        int sharp = middles[RenderScalePolicy.SHARP] >>> 16 & 255, linear = middles[RenderScalePolicy.LINEAR] >>> 16 & 255;
                        if (gpuFailure == null && sharp <= linear + 10)
                            gpuFailure = new IllegalStateException("Sharp is not crisper than Linear (red " + sharp + " vs " + linear + ")");
                        else passed++;
                    }
                }
            });
        }
    }

    private static RenderScalePolicy.Settings test(boolean enabled, double percent, int method, boolean dynamic) {
        return new RenderScalePolicy.Settings(enabled, 0, percent, method, dynamic, 144, 50);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
    private static void fail(Throwable failure) {
        done = true;
        active = false;
        LoggerFactory.getLogger("TheLadsCore").error("Lads render scale probe FAILED after {} checks", passed, failure);
    }
}
