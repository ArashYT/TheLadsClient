package com.thelads.core.v1_8_9.feature.fastquit.mixin;

import com.thelads.core.v1_8_9.feature.fastquit.FastQuit189;
import net.minecraft.world.storage.SaveFormatOld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Delete and rename (which rewrites level.dat) wait for background saves (AnvilSaveConverter inherits both). */
@Mixin(SaveFormatOld.class)
public abstract class SaveFormatFastQuitMixin {
    @Inject(method = "deleteWorldDirectory", at = @At("HEAD"))
    private void lads$awaitSavesBeforeDelete(String folder, CallbackInfoReturnable<Boolean> cir) {
        FastQuit189.awaitSaves();
    }

    @Inject(method = "renameWorld", at = @At("HEAD"))
    private void lads$awaitSavesBeforeRename(String folder, String name, CallbackInfo ci) {
        FastQuit189.awaitSaves();
    }
}
