package com.thelads.core.v1_8_9.utility.mixin;

import com.thelads.core.v1_8_9.utility.Utility189;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Survival Guards: the player's own block break (clickBlock, also reached from onPlayerDamageBlock for a new block) and attack, dropped at
 * HEAD before anything is sent; a stopped break returns false as vanilla's own refusals do.
 */
@Mixin(value = PlayerControllerMP.class, priority = 900) // before the other attack listeners, so a stopped hit is not counted
abstract class GuardsMixin189 {
    @Shadow @Final private Minecraft mc;

    @Inject(method = "clickBlock", at = @At("HEAD"), cancellable = true)
    private void lads$guardBreak(BlockPos pos, EnumFacing face, CallbackInfoReturnable<Boolean> cir) {
        if (Utility189.stopsTool(mc.thePlayer)) cir.setReturnValue(false);
    }

    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
    private void lads$guardAttack(EntityPlayer player, Entity target, CallbackInfo ci) {
        if (player == mc.thePlayer && Utility189.stopsTool(mc.thePlayer)) ci.cancel();
    }
}
