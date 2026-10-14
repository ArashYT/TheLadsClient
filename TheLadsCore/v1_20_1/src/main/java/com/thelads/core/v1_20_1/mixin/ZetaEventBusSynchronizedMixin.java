package com.thelads.core.v1_20_1.mixin;

import com.thelads.core.v1_20_1.compat.ForgeModLoadLock;
import java.lang.reflect.Method;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Synchronizes ForgeZetaEventBus.subscribeMethod / unsubscribeMethod and replaces convertedHandlers with ConcurrentHashMap
 * so Supplementaries and Quark registering listeners concurrently on Zeta.loadBus / Zeta.playBus during parallel Forge
 * CONSTRUCT cannot corrupt Object2ObjectOpenHashMap.
 */
@Pseudo
@Mixin(targets = "org.violetmoon.zetaimplforge.event.ForgeZetaEventBus", remap = false)
public abstract class ZetaEventBusSynchronizedMixin {
    @Inject(method = "<init>", at = @At("TAIL"), remap = false, require = 0)
    private void lads$synchronizeConvertedHandlers(CallbackInfo ci) {
        ForgeModLoadLock.synchronizeMapFields(
            this,
            "org.violetmoon.zetaimplforge.event.ForgeZetaEventBus",
            "convertedHandlers"
        );
    }

    @Inject(method = {"subscribeMethod", "unsubscribeMethod"}, at = @At("HEAD"), remap = false, require = 0)
    private void lads$lockZetaSubscribe(Method method, Object target, Class<?> eventType, CallbackInfo ci) {
        ForgeModLoadLock.ZETA_LOCK.lock();
    }

    @Inject(method = {"subscribeMethod", "unsubscribeMethod"}, at = @At("RETURN"), remap = false, require = 0)
    private void lads$unlockZetaSubscribe(Method method, Object target, Class<?> eventType, CallbackInfo ci) {
        if (ForgeModLoadLock.ZETA_LOCK.isHeldByCurrentThread()) {
            ForgeModLoadLock.ZETA_LOCK.unlock();
        }
    }
}
