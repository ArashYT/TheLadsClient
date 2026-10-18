package com.thelads.smp.features.arise.nb5.mixin.client;

import com.thelads.smp.features.arise.nb5.client.ParkourAnimationManager;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into PlayerModel to animate limbs during ledge grabbing, climbing, vaulting, and sliding.
 */
@Mixin(PlayerModel.class)
public abstract class MixinPlayerModel {

    @Inject(
            method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V",
            at = @At("TAIL")
    )
    private void ladssmp$applyParkourLimbPoses(AvatarRenderState state, CallbackInfo ci) {
        PlayerModel self = (PlayerModel) (Object) this;
        ParkourAnimationManager.applyPlayerModelAnim(self, state);
    }
}
