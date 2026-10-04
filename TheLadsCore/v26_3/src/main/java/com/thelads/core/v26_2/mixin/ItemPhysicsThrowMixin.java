package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.NativeItemPhysics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Item Physics' charged throw: the thrown item leaves the hand faster, before it enters the level. */
@Mixin(LivingEntity.class)
public class ItemPhysicsThrowMixin {
    @Inject(method = "createItemStackToDrop", at = @At("RETURN"), require = 1)
    private void lads$chargedThrow(ItemStack stack, boolean randomly, boolean thrownFromHand, CallbackInfoReturnable<ItemEntity> callback) {
        if (thrownFromHand && !randomly) NativeItemPhysics.thrown((LivingEntity) (Object) this, callback.getReturnValue());
    }
}
