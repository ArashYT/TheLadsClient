package com.thelads.core.v26_2.feature.fastquit.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.SDLEventHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import com.thelads.core.v26_2.feature.fastquit.FastQuit26;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Leaving a singleplayer world returns without waiting for its server; closing the game waits for background saves first. */
@Mixin(Minecraft.class)
abstract class MinecraftFastQuitMixin implements FastQuit26.Pump {
    @Shadow @Final private SDLEventHandler sdlEventHandler;
    @Shadow private IntegratedServer singleplayerServer;
    @Shadow public abstract void renderFrame(boolean advanceGameTime);
    @Unique private long lads$quitStart;

    @Override
    public void lads$fastQuitFrame() {
        RenderSystem.pumpEvents(sdlEventHandler);
        renderFrame(false);
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("HEAD"))
    private void lads$quitStarts(Screen next, boolean keepResourcePacks, boolean b, CallbackInfo ci) {
        lads$quitStart = singleplayerServer != null ? System.nanoTime() : 0L;
    }

    @WrapOperation(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/server/IntegratedServer;isShutdown()Z"))
    private boolean lads$skipServerWait(IntegratedServer server, Operation<Boolean> original) {
        return FastQuit26.skipServerWait((Minecraft) (Object) this, server) || original.call(server);
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("RETURN"))
    private void lads$quitDone(Screen next, boolean keepResourcePacks, boolean b, CallbackInfo ci) {
        if (lads$quitStart == 0L) return;
        LoggerFactory.getLogger("TheLadsCore").info("Lads FastQuit: left the singleplayer world in {} ms",
            (System.nanoTime() - lads$quitStart) / 1_000_000);
        lads$quitStart = 0L;
    }

    /** Before the world teardown and Minecraft.close (which shuts the executors the saving server still uses). */
    @Inject(method = "exitWorldAndClose", at = @At("HEAD"))
    private void lads$awaitSavesBeforeClose(CallbackInfo ci) {
        FastQuit26.awaitSaves(false);
    }
}
