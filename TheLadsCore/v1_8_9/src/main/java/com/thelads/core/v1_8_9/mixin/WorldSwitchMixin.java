package com.thelads.core.v1_8_9.mixin;

import net.minecraft.client.LoadingScreenRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Instant world switches: opening and leaving singleplayer worlds, joining servers, changing dimension. 1.8.9 forced a full
 * garbage collection on each (up to three when a world opens; the JVM collects on its own), checked the starting integrated
 * server only every 200 ms, redrew the whole loading screen on every check, and drew it once more for every world it
 * switched to, though "Downloading terrain" replaces it at once. Leaving a world still shows "Shutting down internal
 * server" while the world saves.
 */
@Mixin(Minecraft.class)
public abstract class WorldSwitchMixin {
    @Unique private boolean ladsToWorld;
    @Unique private String ladsShown;
    @Unique private long ladsShownAt;

    @Redirect(method = "launchIntegratedServer", at = @At(value = "INVOKE", target = "Ljava/lang/System;gc()V"), require = 1)
    private void ladsNoGcOnOpen() {}

    @Redirect(method = "loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V",
        at = @At(value = "INVOKE", target = "Ljava/lang/System;gc()V"), require = 1)
    private void ladsNoGcOnSwitch() {}

    /** The integrated server is ready when its run loop starts: checked every 5 ms instead of 200. */
    @Redirect(method = "launchIntegratedServer", at = @At(value = "INVOKE", target = "Ljava/lang/Thread;sleep(J)V"), require = 1)
    private void ladsCheckSooner(long millis) throws InterruptedException {
        Thread.sleep(Math.min(millis, 5L));
    }

    /** "Loading world" / "Building terrain 40%": redrawn when the text changes, at most every 100 ms otherwise. */
    @Redirect(method = "launchIntegratedServer",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/LoadingScreenRenderer;displayLoadingString(Ljava/lang/String;)V"), require = 1)
    private void ladsRedrawWhenNeeded(LoadingScreenRenderer screen, String message) {
        long now = Minecraft.getSystemTime();
        if (message.equals(ladsShown) && now - ladsShownAt < 100L) return;
        ladsShown = message;
        ladsShownAt = now;
        screen.displayLoadingString(message);
    }

    @Inject(method = "loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V", at = @At("HEAD"), require = 1)
    private void ladsSwitching(WorldClient world, String message, CallbackInfo ci) {
        ladsToWorld = world != null;
        ladsShown = null;
    }

    /** Switching to a world (joining, changing dimension): no loading-screen frame before "Downloading terrain". */
    @Redirect(method = "loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/LoadingScreenRenderer;resetProgressAndMessage(Ljava/lang/String;)V"), require = 1)
    private void ladsNoResetFrame(LoadingScreenRenderer screen, String message) {
        if (!ladsToWorld) screen.resetProgressAndMessage(message);
    }

    @Redirect(method = "loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/LoadingScreenRenderer;displayLoadingString(Ljava/lang/String;)V"), require = 1)
    private void ladsNoSwitchFrame(LoadingScreenRenderer screen, String message) {
        if (!ladsToWorld) screen.displayLoadingString(message);
    }
}
