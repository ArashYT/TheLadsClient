package com.thelads.core.v26_2.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.v26_2.feature.LadsAvatarRenderState;
import com.thelads.core.v26_2.feature.NametagBadgeContext;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("RETURN"), require = 1)
    private void lads$extractBadge(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
        ((LadsAvatarRenderState) state).lads$setUuid(entity.getUUID());
    }

    @Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"), require = 1)
    private void lads$beforeSubmitName(AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera, CallbackInfo ci) {
        if (((LadsAvatarRenderState) state).lads$hasBadge()) {
            NametagBadgeContext.CURRENT_PLAYER = ((LadsAvatarRenderState) state).lads$getUuid();
            NametagBadgeContext.CURRENT_NAMETAG = state.nameTag;
        }
    }

    @Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("RETURN"), require = 1)
    private void lads$afterSubmitName(AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera, CallbackInfo ci) {
        NametagBadgeContext.CURRENT_PLAYER = null;
        NametagBadgeContext.CURRENT_NAMETAG = null;
    }
}
