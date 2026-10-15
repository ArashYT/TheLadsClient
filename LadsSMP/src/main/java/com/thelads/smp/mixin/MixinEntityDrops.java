package com.thelads.smp.mixin;

import com.thelads.smp.disabler.ItemDisablerSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Pathway 8: Prevents disabled items from being dropped via Entity#spawnAtLocation (mob drops, shearing, gifts, fishing).
 */
@Mixin(Entity.class)
public abstract class MixinEntityDrops {

    @Inject(
            method = "spawnAtLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void ladssmp$blockDisabledEntityDrop(ServerLevel level, ItemStack stack, Vec3 offset, CallbackInfoReturnable<ItemEntity> cir) {
        if (ItemDisablerSystem.isDisabled(stack)) {
            cir.setReturnValue(null);
        }
    }
}
