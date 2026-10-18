package com.thelads.smp.features.arise.nb1.mixin;

import com.thelads.smp.features.arise.nb1.ForgivingBorderFeature;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin target: net.minecraft.server.level.ServerPlayer
 * Method: public void tick();
 */
@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayerBorder {

    @Inject(method = "tick", at = @At("HEAD"))
    private void ladssmp$forgivingBorderCheck(CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        ForgivingBorderFeature.handlePlayerOutsideBorder(player);
    }
}
