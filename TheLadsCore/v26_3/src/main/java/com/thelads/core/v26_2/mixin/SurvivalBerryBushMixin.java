package com.thelads.core.v26_2.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.modules.SurvivalTweaksModule;
import com.thelads.core.v26_2.feature.survivaltweaks.NativeSurvivalTweaks;
import net.minecraft.server.level.ServerLevel;
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

@Mixin(SweetBerryBushBlock.class)
public abstract class SurvivalBerryBushMixin {
    @WrapOperation(
            method = "entityInside",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;makeStuckInBlock(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/phys/Vec3;)V")
    )
    private void lads$reduceBerryBushSlowWhenArmored(Entity entity, BlockState state, Vec3 multiplier, Operation<Void> original) {
        SurvivalTweaksModule mod = SurvivalTweaksModule.get();
        if (NativeSurvivalTweaks.active() && mod != null && mod.isBerryBushProtectionEnabled()
                && entity instanceof LivingEntity living
                && (!living.getItemBySlot(EquipmentSlot.LEGS).isEmpty() || !living.getItemBySlot(EquipmentSlot.BODY).isEmpty())) {
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
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z")
    )
    private boolean lads$preventBerryBushDamage(Entity entity, ServerLevel level, DamageSource source, float amount, Operation<Boolean> original) {
        SurvivalTweaksModule mod = SurvivalTweaksModule.get();
        if (NativeSurvivalTweaks.active() && mod != null && mod.isBerryBushProtectionEnabled()) {
            if (entity instanceof Player && entity.isSteppingCarefully()) {
                return false;
            }
            if (entity instanceof LivingEntity living
                    && (!living.getItemBySlot(EquipmentSlot.LEGS).isEmpty() || !living.getItemBySlot(EquipmentSlot.BODY).isEmpty())) {
                return false;
            }
        }
        return original.call(entity, level, source, amount);
    }
}
