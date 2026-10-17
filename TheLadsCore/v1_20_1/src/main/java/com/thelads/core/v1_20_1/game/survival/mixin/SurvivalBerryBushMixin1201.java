package com.thelads.core.v1_20_1.game.survival.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.modules.SurvivalTweaksModule;
import com.thelads.core.v1_20_1.game.survival.NativeSurvivalTweaks1201;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** SurvivalTweaks: Berry bush protection. Leggings soften the slowdown and stop the thorns; sneaking stops them for a player. */
@Mixin(SweetBerryBushBlock.class)
public abstract class SurvivalBerryBushMixin1201 {
    @WrapOperation(
            method = "entityInside",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;makeStuckInBlock(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/phys/Vec3;)V")
    )
    private void lads$reduceBerryBushSlowWhenArmored(Entity entity, BlockState state, Vec3 multiplier, Operation<Void> original) {
        SurvivalTweaksModule mod = SurvivalTweaksModule.get();
        if (NativeSurvivalTweaks1201.active() && mod != null && mod.isBerryBushProtectionEnabled()
                && entity instanceof LivingEntity living
                && !living.getItemBySlot(EquipmentSlot.LEGS).isEmpty()) {
            Vec3 reduced = new Vec3(
                    Math.min(multiplier.x * 1.1D, 1.0D),
                    Math.min(multiplier.y * 1.1D, 1.0D),
                    Math.min(multiplier.z * 1.1D, 1.0D)
            );
            original.call(entity, state, reduced);
            return;
        }
        original.call(entity, state, multiplier);
    }

    @WrapOperation(
            method = "entityInside",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z")
    )
    private boolean lads$preventBerryBushDamage(Entity entity, DamageSource source, float amount, Operation<Boolean> original) {
        SurvivalTweaksModule mod = SurvivalTweaksModule.get();
        if (NativeSurvivalTweaks1201.active() && mod != null && mod.isBerryBushProtectionEnabled()) {
            if (entity instanceof Player && entity.isSteppingCarefully()) {
                return false;
            }
            if (entity instanceof LivingEntity living && !living.getItemBySlot(EquipmentSlot.LEGS).isEmpty()) {
                return false;
            }
        }
        return original.call(entity, source, amount);
    }
}
