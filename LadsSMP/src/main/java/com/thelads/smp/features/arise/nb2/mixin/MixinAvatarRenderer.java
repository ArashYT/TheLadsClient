package com.thelads.smp.features.arise.nb2.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.smp.features.arise.nb2.HideNameManager;
import com.thelads.smp.features.arise.nb2.RealisticNametagManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to AvatarRenderer to support client-side overhead nametag hiding (HideName)
 * and obstruction-based line-of-sight nametag occlusion (Realistic Nametag).
 */
@Environment(EnvType.CLIENT)
@Mixin(AvatarRenderer.class)
public abstract class MixinAvatarRenderer {

    @Inject(
            method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onSubmitNameDisplay(AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        if (HideNameManager.isHidingNametags()) {
            ci.cancel();
            return;
        }

        if (camera != null && camera.pos != null) {
            double tagY = state.y + (state.nameTagAttachment != null ? state.nameTagAttachment.y : state.boundingBoxHeight + 0.5);
            Vec3 tagPos = new Vec3(state.x, tagY, state.z);
            if (RealisticNametagManager.isObstructed(state.id, camera.pos, tagPos, state.isSpectator)) {
                ci.cancel();
            }
        }
    }
}
