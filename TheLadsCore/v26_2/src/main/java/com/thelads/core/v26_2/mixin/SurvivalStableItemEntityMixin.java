package com.thelads.core.v26_2.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.thelads.core.modules.SurvivalTweaksModule;
import com.thelads.core.v26_2.feature.survivaltweaks.NativeSurvivalTweaks;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemEntity.class)
public abstract class SurvivalStableItemEntityMixin {
    @WrapOperation(
            method = "<init>(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/item/ItemEntity;setDeltaMovement(DDD)V")
    )
    private void lads$stabilizeDroppedItemMovement(
            ItemEntity instance,
            double dx,
            double dy,
            double dz,
            Operation<Void> original,
            @Local(argsOnly = true) Level level
    ) {
        SurvivalTweaksModule mod = SurvivalTweaksModule.get();
        if (NativeSurvivalTweaks.active() && mod != null && mod.isStableBlockDropsEnabled()) {
            Player closestSneaking = level.getNearestPlayer(
                    instance.getX(),
                    instance.getY(),
                    instance.getZ(),
                    2.5D,
                    EntitySelector.NO_SPECTATORS.and(Entity::isSteppingCarefully)
            );
            if (closestSneaking != null) {
                double dist = closestSneaking.distanceTo(instance);
                if (dist > 0.01D) {
                    double rx = closestSneaking.getX() - instance.getX();
                    double ry = closestSneaking.getY() - instance.getY();
                    double rz = closestSneaking.getZ() - instance.getZ();
                    double yVel = dy + Mth.lerp(ry / dist, 0.0D, dist / 2.5D) * 0.25D;
                    original.call(instance, rx * 0.1D, yVel, rz * 0.1D);
                    return;
                }
            }
            original.call(instance, 0.0D, dy, 0.0D);
            return;
        }
        original.call(instance, dx, dy, dz);
    }
}
