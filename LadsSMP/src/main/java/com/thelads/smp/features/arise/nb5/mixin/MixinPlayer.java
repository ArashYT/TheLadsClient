package com.thelads.smp.features.arise.nb5.mixin;

import com.thelads.smp.features.arise.nb5.server.ParkourServerManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into Player:
 * - Persists stamina data to world storage via addAdditionalSaveData / readAdditionalSaveData
 * - Hooks causeFallDamage to apply breakfall damage mitigation
 */
@Mixin(Player.class)
public abstract class MixinPlayer {

    @Inject(
            method = "addAdditionalSaveData(Lnet/minecraft/world/level/storage/ValueOutput;)V",
            at = @At("RETURN")
    )
    private void ladssmp$saveParkourStamina(ValueOutput output, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        ParkourServerManager.getOrCreateData(self.getUUID()).writeSaveData(output);
    }

    @Inject(
            method = "readAdditionalSaveData(Lnet/minecraft/world/level/storage/ValueInput;)V",
            at = @At("RETURN")
    )
    private void ladssmp$loadParkourStamina(ValueInput input, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        ParkourServerManager.getOrCreateData(self.getUUID()).readSaveData(input);
    }

    @ModifyVariable(
            method = "causeFallDamage(DFLnet/minecraft/world/damagesource/DamageSource;)Z",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private double ladssmp$applyBreakfallReduction(double fallDistance, double originalDistance, float damageMultiplier, DamageSource damageSource) {
        Player self = (Player) (Object) this;
        return ParkourServerManager.applyBreakfallDamageReduction(self, fallDistance);
    }
}
