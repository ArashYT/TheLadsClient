package com.thelads.core.v1_21_11.mixin;

import com.thelads.core.v1_21_11.feature.NativeOldAnimations;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.7 third person, decided at extraction: the sword block's arm poses and hidden shield, and flat held items for 1.7's placement. */
@Mixin(AvatarRenderer.class)
public class OldAnimationsAvatarMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
        at = @At("TAIL"), require = 1)
    private void lads$oldThirdPerson(Avatar avatar, AvatarRenderState state, float partial, CallbackInfo ci) {
        NativeOldAnimations.extract(avatar, state);
    }
}
