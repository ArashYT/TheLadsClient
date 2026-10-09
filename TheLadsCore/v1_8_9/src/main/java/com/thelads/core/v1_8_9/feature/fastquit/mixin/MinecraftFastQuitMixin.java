package com.thelads.core.v1_8_9.feature.fastquit.mixin;

import com.thelads.core.v1_8_9.feature.fastquit.FastQuit189;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.world.WorldSettings;
import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Leaving a singleplayer world returns without waiting for its server. Opening a world (before WorldBackup189's newer-version
 * check reads or copies it: priority 500 runs first) and closing the game wait for background saves.
 */
@Mixin(value = Minecraft.class, priority = 500)
public abstract class MinecraftFastQuitMixin {
    @Shadow volatile boolean running;
    @Shadow private IntegratedServer theIntegratedServer;
    @Unique private long lads$quitStart;

    @Inject(method = "loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V", at = @At("HEAD"))
    private void lads$quitStarts(WorldClient world, String message, CallbackInfo ci) {
        lads$quitStart = world == null && theIntegratedServer != null ? System.nanoTime() : 0L;
    }

    @Redirect(method = "loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/integrated/IntegratedServer;isServerStopped()Z"))
    private boolean lads$skipServerWait(IntegratedServer server) {
        return FastQuit189.skipServerWait((Minecraft) (Object) this, server, running) || server.isServerStopped();
    }

    @Inject(method = "loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V", at = @At("RETURN"))
    private void lads$quitDone(WorldClient world, String message, CallbackInfo ci) {
        if (lads$quitStart == 0L) return;
        LogManager.getLogger("TheLadsCore").info("Lads FastQuit: left the singleplayer world in {} ms", (System.nanoTime() - lads$quitStart) / 1_000_000);
        lads$quitStart = 0L;
    }

    @Inject(method = "launchIntegratedServer", at = @At("HEAD"))
    private void lads$awaitSavesBeforeOpen(String folder, String name, WorldSettings settings, CallbackInfo ci) {
        FastQuit189.awaitSaves();
    }

    /** The window's close button, Quit Game: the game loop still runs here, so the wait shows its loading screen. */
    @Inject(method = "shutdown", at = @At("HEAD"))
    private void lads$awaitSavesOnQuit(CallbackInfo ci) {
        FastQuit189.awaitSaves();
    }

    /** Every exit through the game loop's end: before System.exit. */
    @Inject(method = "shutdownMinecraftApplet", at = @At("HEAD"))
    private void lads$awaitSavesAtExit(CallbackInfo ci) {
        FastQuit189.awaitSavesAtExit();
    }
}
