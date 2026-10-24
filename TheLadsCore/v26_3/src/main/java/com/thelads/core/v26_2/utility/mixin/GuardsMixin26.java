package com.thelads.core.v26_2.utility.mixin;

import com.thelads.core.v26_2.utility.Utility26;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Survival Guards: the player's own break, attack and use-on-block, dropped at HEAD before anything is sent. A stopped break returns
 * false as vanilla's own refusals do (adventure mode, world border); a stopped strip returns FAIL so the other hand is not tried.
 */
@Mixin(value = MultiPlayerGameMode.class, priority = 900) // before the attack listeners (Kill Banner, Reach Display), so a stopped hit is not counted
abstract class GuardsMixin26 {
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void lads$guardBreak(BlockPos pos, Direction face, CallbackInfoReturnable<Boolean> cir) {
        if (minecraft.player != null && minecraft.level != null && Utility26.stopsBreak(minecraft.player, minecraft.level.getBlockState(pos)))
            cir.setReturnValue(false);
    }

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void lads$guardAttack(Player player, Entity target, CallbackInfo ci) {
        if (player instanceof LocalPlayer local && Utility26.stopsTool(local)) ci.cancel();
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void lads$guardStrip(LocalPlayer player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (minecraft.level != null && Utility26.stopsStrip(player, player.getItemInHand(hand), minecraft.level.getBlockState(hit.getBlockPos())))
            cir.setReturnValue(InteractionResult.FAIL);
    }
}
