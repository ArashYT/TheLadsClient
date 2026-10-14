package com.thelads.core.v1_20_1.mixin;

import com.thelads.core.v1_20_1.compat.ForgeModLoadLock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Serializes Create.onCtor() against CreateDecoMod.init() during parallel Forge CONSTRUCT so CreateDecoMod cannot
 * trigger AllItems.<clinit>() on its worker thread while Create is still executing AllBlocks.register().
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.Create", remap = false)
public abstract class CreateCtorSynchronizedMixin {
    @Inject(method = "onCtor()V", at = @At("HEAD"), remap = false, require = 0)
    private static void lads$lockCreateCtor(CallbackInfo ci) {
        ForgeModLoadLock.CREATE_LOCK.lock();
    }

    @Inject(method = "onCtor()V", at = @At("RETURN"), remap = false, require = 0)
    private static void lads$unlockCreateCtor(CallbackInfo ci) {
        if (ForgeModLoadLock.CREATE_LOCK.isHeldByCurrentThread()) {
            ForgeModLoadLock.CREATE_LOCK.unlock();
        }
    }
}
