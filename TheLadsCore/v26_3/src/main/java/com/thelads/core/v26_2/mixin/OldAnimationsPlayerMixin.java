package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.NativeOldAnimations;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public class OldAnimationsPlayerMixin {
    // 1.7 Animations' third person: the sword block's arm poses and hidden shield, and flat held items prepared for 1.7's placement.
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
        at = @At("TAIL"), require = 1)
    private void lads$oldThirdPerson(Avatar entity, AvatarRenderState state, float partialTick, CallbackInfo callback) {
        NativeOldAnimations.thirdPerson(entity, state);
    }
}
