package com.thelads.smp.features.arise.nb1.mixin;

import com.thelads.smp.features.arise.nb1.DimensionAccessFeature;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin target: net.minecraft.server.level.ServerPlayer
 * Method: public net.minecraft.server.level.ServerPlayer teleport(net.minecraft.world.level.portal.TeleportTransition);
 */
@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayerDimension {

    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;", at = @At("HEAD"), cancellable = true)
    private void ladssmp$checkDimensionAccess(TeleportTransition transition, CallbackInfoReturnable<ServerPlayer> cir) {
        if (transition != null && transition.newLevel() != null) {
            ServerPlayer player = (ServerPlayer) (Object) this;
            String destDimension = transition.newLevel().dimension().identifier().toString();

            if (DimensionAccessFeature.isDimensionDenied(player, destDimension)) {
                DimensionAccessFeature.sendDeniedFeedback(player, destDimension);
                player.setPortalCooldown(40);
                cir.setReturnValue(player);
            }
        }
    }
}
