package com.thelads.core.v1_20_1.game.misc.mixin;

import com.thelads.core.v1_20_1.game.misc.NativeItemPhysics1201;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Item Physics' charged throw: the thrown item leaves the hand faster, before it enters the level (ServerPlayer.drop adds it after). */
@Mixin(Player.class)
public class ItemPhysicsThrowMixin {
    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("RETURN"), require = 1)
    private void lads$chargedThrow(ItemStack stack, boolean randomly, boolean thrownFromHand, CallbackInfoReturnable<ItemEntity> callback) {
        if (thrownFromHand && !randomly) NativeItemPhysics1201.thrown((Player) (Object) this, callback.getReturnValue());
    }
}
