package com.thelads.smp.features.arise.nb2.mixin;

import com.thelads.smp.features.arise.nb2.HoldMyBeerManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to MultiPlayerGameMode to treat the player's held hand as virtual ItemStack.EMPTY (Hold My Beer).
 * This permits empty-hand block interactions (doors, chests, levers) while suppressing held item placement.
 */
@Environment(EnvType.CLIENT)
@Mixin(MultiPlayerGameMode.class)
public abstract class MixinMultiPlayerGameMode {

    @Inject(
            method = "useItem(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onUseItem(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (HoldMyBeerManager.shouldSuppressUse(hand)) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

    @Redirect(
            method = "performUseItemOn(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;getItemInHand(Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/item/ItemStack;"
            )
    )
    private ItemStack onGetItemInHandDuringUseOn(LocalPlayer player, InteractionHand hand) {
        if (HoldMyBeerManager.shouldSuppressUse(hand)) {
            return ItemStack.EMPTY;
        }
        return player.getItemInHand(hand);
    }
}
