package com.thelads.core.v26_2.feature.fastquit.mixin;

import com.thelads.core.v26_2.feature.fastquit.FastQuit26;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Every open of world storage (join, create, edit, delete, re-create, backup) waits for background saves first. */
@Mixin(LevelStorageSource.class)
abstract class LevelStorageSourceFastQuitMixin {
    @Inject(method = {"createAccess", "validateAndCreateAccess"}, at = @At("HEAD"), require = 2)
    private void lads$awaitSaves(String levelId, CallbackInfoReturnable<LevelStorageSource.LevelStorageAccess> cir) {
        FastQuit26.awaitSaves(true);
    }
}
