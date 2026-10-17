package com.thelads.core.v1_20_1.game.survival.mixin;

import com.thelads.core.modules.SurvivalTweaksModule;
import com.thelads.core.v1_20_1.game.survival.NativeSurvivalTweaks1201;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * SurvivalTweaks: Stable block drops (2/2). A freshly dropped item does not scatter sideways; near a sneaking player it drifts to
 * that player. 26.3 wraps the constructor's setDeltaMovement. Here the five-argument constructor only delegates to the
 * eight-argument one, which popResourceFromFace and others call with a deliberate push, so the motion is corrected after it.
 */
@Mixin(ItemEntity.class)
public abstract class SurvivalStableItemEntityMixin1201 {
    @Inject(method = "<init>(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;)V", at = @At("TAIL"))
    private void lads$stabilizeDroppedItemMovement(Level level, double x, double y, double z, ItemStack stack, CallbackInfo ci) {
        SurvivalTweaksModule mod = SurvivalTweaksModule.get();
        if (!NativeSurvivalTweaks1201.active() || mod == null || !mod.isStableBlockDropsEnabled()) {
            return;
        }
        ItemEntity self = (ItemEntity) (Object) this;
        double dy = self.getDeltaMovement().y;
        Player closestSneaking = level.getNearestPlayer(x, y, z, 2.5D, EntitySelector.NO_SPECTATORS.and(Entity::isSteppingCarefully));
        if (closestSneaking != null) {
            double dist = closestSneaking.distanceTo(self);
            if (dist > 0.01D) {
                double rx = closestSneaking.getX() - x;
                double ry = closestSneaking.getY() - y;
                double rz = closestSneaking.getZ() - z;
                double yVel = dy + Mth.lerp(ry / dist, 0.0D, dist / 2.5D) * 0.25D;
                self.setDeltaMovement(rx * 0.1D, yVel, rz * 0.1D);
                return;
            }
        }
        self.setDeltaMovement(0.0D, dy, 0.0D);
    }
}
