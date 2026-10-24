package com.thelads.core.v26_2.mixin.pvp;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.client.PvpVisuals;
import com.thelads.core.v26_2.feature.pvp.PvpVisuals26;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Render Tweaks' Entity Shadows: extractShadow reads vanilla's Entity Shadows video option first; off here, it extracts none.
 * Nametags on players: submitNameDisplay sets the tag's Scale and Distance Fade for SubmitNodeCollectionTagMixin26 and adds Show
 * Ping and Nametag Health after the name.
 */
@Mixin(EntityRenderer.class)
public class EntityRendererPvpMixin26 {
    private static final String NAME_DISPLAY = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"
        + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V";
    private static final String SUBMIT_TAG = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitNameTag(Lcom/mojang/blaze3d/vertex/PoseStack;"
        + "Lnet/minecraft/world/phys/Vec3;ILnet/minecraft/network/chat/Component;ZILnet/minecraft/client/renderer/state/level/CameraRenderState;)V";

    @ModifyExpressionValue(method = "extractShadow", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"))
    private Object lads$shadows(Object shown) {
        return PvpVisuals.hideShadows ? Boolean.FALSE : shown;
    }

    @Inject(method = NAME_DISPLAY, at = @At("HEAD"))
    private void lads$tagStart(EntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, int offset, CallbackInfo ci) {
        PvpVisuals.tagStart(state instanceof AvatarRenderState, state.distanceToCameraSq);
    }

    /** After the name tag's own submit (the score line above it is the first). */
    @Inject(method = NAME_DISPLAY, at = @At(value = "INVOKE", target = SUBMIT_TAG, ordinal = 1, shift = At.Shift.AFTER))
    private void lads$tagExtras(EntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, int offset, CallbackInfo ci) {
        PvpVisuals26.tagExtras(state, pose, collector, camera, offset);
    }

    @Inject(method = NAME_DISPLAY, at = @At("TAIL"))
    private void lads$tagEnd(EntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, int offset, CallbackInfo ci) {
        PvpVisuals.tagStart(false, 0);
    }
}
