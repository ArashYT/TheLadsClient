package com.thelads.core.v1_20_1.feature.fastquit.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.v1_20_1.feature.fastquit.FastQuit1201;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Leaving a singleplayer world returns without waiting for its server; closing the game waits for background saves first.
 * Forge's clearLevel keeps vanilla's wait loop (with its level-unload and player-logout hooks around it), so this applies there too.
 */
@Mixin(Minecraft.class)
abstract class MinecraftFastQuitMixin implements FastQuit1201.Pump {
    @Shadow private IntegratedServer singleplayerServer;
    @Shadow private void runTick(boolean renderLevel) { throw new AssertionError(); }
    @Unique private long lads$quitStart;

    @Override
    public void lads$fastQuitFrame() {
        runTick(false);
    }

    @Inject(method = "clearLevel(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("HEAD"))
    private void lads$quitStarts(Screen next, CallbackInfo ci) {
        lads$quitStart = singleplayerServer != null ? System.nanoTime() : 0L;
        // No level: vanilla drops the server without stopping or waiting for it (its wait loop below needs a level).
        if (singleplayerServer != null && ((Minecraft) (Object) this).level == null) FastQuit1201.stopInBackground((Minecraft) (Object) this, singleplayerServer);
    }

    @WrapOperation(method = "clearLevel(Lnet/minecraft/client/gui/screens/Screen;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/server/IntegratedServer;isShutdown()Z"))
    private boolean lads$skipServerWait(IntegratedServer server, Operation<Boolean> original) {
        return FastQuit1201.stopInBackground((Minecraft) (Object) this, server) || original.call(server);
    }

    /**
     * doWorldLoad leaves the current world itself (a world opened while one is loaded, after the world storage wait): the new
     * server starts once the last one has fully stopped, so that no mod ever sees two.
     */
    @Inject(method = "doWorldLoad", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;clearLevel()V", shift = At.Shift.AFTER))
    private void lads$awaitLastServer(CallbackInfo ci) {
        FastQuit1201.awaitSaves(false);
    }

    @Inject(method = "clearLevel(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("RETURN"))
    private void lads$quitDone(Screen next, CallbackInfo ci) {
        if (lads$quitStart == 0L) return;
        LoggerFactory.getLogger("TheLadsCore").info("Lads FastQuit: left the singleplayer world in {} ms",
            (System.nanoTime() - lads$quitStart) / 1_000_000);
        lads$quitStart = 0L;
    }

    /** Before the world teardown and Minecraft.close (which shuts the executors the saving server still uses). */
    @Inject(method = "destroy", at = @At("HEAD"))
    private void lads$awaitSavesBeforeClose(CallbackInfo ci) {
        FastQuit1201.awaitSaves(false);
    }
}
