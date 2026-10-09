package com.thelads.core.v1_8_9.feature.fastquit.mixin;

import com.thelads.core.v1_8_9.feature.fastquit.FastQuit189;
import net.minecraft.world.chunk.storage.AnvilSaveConverter;
import net.minecraft.world.storage.ISaveHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A save handler stamps the world's session.lock (the saving server would then refuse to write: "The save is being accessed
 * from another location") and flushCache closes every cached region file, the saving server's included. Both wait for
 * background saves: opening a world, re-creating one, deleting one.
 */
@Mixin(AnvilSaveConverter.class)
public abstract class AnvilSaveConverterFastQuitMixin {
    @Inject(method = "getSaveLoader", at = @At("HEAD"))
    private void lads$awaitSavesBeforeOpen(String folder, boolean players, CallbackInfoReturnable<ISaveHandler> cir) {
        FastQuit189.awaitSaves();
    }

    @Inject(method = "flushCache", at = @At("HEAD"))
    private void lads$awaitSavesBeforeFlush(CallbackInfo ci) {
        FastQuit189.awaitSaves();
    }
}
