package com.thelads.core.v1_20_1.mixin;

import com.thelads.core.v1_20_1.compat.ForgeModLoadLock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Serializes CreateDecoMod.init() against Create.onCtor() during parallel Forge CONSTRUCT so Create's AllBlocks and
 * AllItems registrations complete cleanly before Create Deco references AllItems.ANDESITE_ALLOY.
 */
@Pseudo
@Mixin(targets = "com.github.talrey.createdeco.CreateDecoMod", remap = false)
public abstract class CreateDecoInitSynchronizedMixin {
    @Inject(method = "init()V", at = @At("HEAD"), remap = false, require = 0)
    private static void lads$lockCreateDecoInit(CallbackInfo ci) {
        ForgeModLoadLock.CREATE_LOCK.lock();
    }

    @Inject(method = "init()V", at = @At("RETURN"), remap = false, require = 0)
    private static void lads$unlockCreateDecoInit(CallbackInfo ci) {
        if (ForgeModLoadLock.CREATE_LOCK.isHeldByCurrentThread()) {
            ForgeModLoadLock.CREATE_LOCK.unlock();
        }
    }
}
