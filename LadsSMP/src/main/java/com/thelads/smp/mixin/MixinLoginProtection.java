package com.thelads.smp.mixin;

import com.thelads.smp.features.LoginProtectionManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Consolidates 'logprot': Enforces login invulnerability in ServerPlayer.hurtServer
 * and refreshes protection upon dimension transition and respawn.
 */
@Mixin(ServerPlayer.class)
public abstract class MixinLoginProtection {

    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void ladssmp$preventDamageDuringLoginProtection(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (LoginProtectionManager.isProtected(self, source)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "restoreFrom", at = @At("RETURN"))
    private void ladssmp$protectOnRespawn(ServerPlayer oldPlayer, boolean keepEverything, CallbackInfo ci) {
        LoginProtectionManager.protect((ServerPlayer) (Object) this);
    }

    @Inject(method = "triggerDimensionChangeTriggers", at = @At("HEAD"))
    private void ladssmp$protectOnDimensionChange(ServerLevel origin, CallbackInfo ci) {
        LoginProtectionManager.protect((ServerPlayer) (Object) this);
    }
}
