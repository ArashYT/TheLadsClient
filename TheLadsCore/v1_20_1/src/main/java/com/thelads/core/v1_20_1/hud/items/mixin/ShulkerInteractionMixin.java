package com.thelads.core.v1_20_1.hud.items.mixin;

import com.thelads.core.v1_20_1.hud.items.ShulkerContents1201;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The box the player just used: the menu the server opens next may be its contents. */
@Mixin(MultiPlayerGameMode.class)
abstract class ShulkerInteractionMixin {
    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void lads$clicked(LocalPlayer player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> callback) {
        ShulkerContents1201.clicked(hit.getBlockPos());
    }
}
