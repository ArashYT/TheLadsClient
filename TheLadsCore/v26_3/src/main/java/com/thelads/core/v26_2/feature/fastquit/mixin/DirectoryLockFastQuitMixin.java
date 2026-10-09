package com.thelads.core.v26_2.feature.fastquit.mixin;

import com.thelads.core.client.FastQuit;
import java.nio.file.Path;
import net.minecraft.util.DirectoryLock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The world list asks whether each world is locked. A world saving in the background holds its session.lock in this
 * game, which the check can't read (it throws), and the world would drop out of the list: it is listed as unlocked
 * instead, marked "Saving..." (LevelSummaryFastQuitMixin), and opening it waits for the save.
 */
@Mixin(DirectoryLock.class)
abstract class DirectoryLockFastQuitMixin {
    @Inject(method = "isLocked", at = @At("HEAD"), cancellable = true)
    private static void lads$savingIsNotLocked(Path worldDir, CallbackInfoReturnable<Boolean> cir) {
        if (FastQuit.saving(worldDir)) cir.setReturnValue(false);
    }
}
