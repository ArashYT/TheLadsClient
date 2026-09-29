package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.ShulkerContents;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public class ShulkerInteractionMixin {
    @Inject(method = "useItemOn", at = @At("HEAD"), require = 1)
    private void lads$clicked(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
                              CallbackInfoReturnable<InteractionResult> callback) {
        ShulkerContents.clicked(hit.getBlockPos());
    }
}
