package com.thelads.core.v26_2.mixin.pvp;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.client.PvpVisuals;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Nametags' Scale and Distance Fade on the tag EntityRendererPvpMixin26 marked (1 and no fade for other tags). */
@Mixin(SubmitNodeCollection.class)
public class SubmitNodeCollectionTagMixin26 {
    private static final String NAME_TAG = "Lnet/minecraft/client/renderer/SubmitNodeCollection;nameTag(Lorg/joml/Matrix4f;FFLnet/minecraft/util/FormattedCharSequence;"
        + "IIILnet/minecraft/client/gui/Font$DisplayMode;)Lnet/minecraft/client/renderer/feature/TextFeatureRenderer$Submit;";

    @Inject(method = "submitNameTag", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V", shift = At.Shift.AFTER))
    private void lads$tagScale(PoseStack pose, Vec3 attachment, int offset, Component name, boolean seeThrough, int light, CameraRenderState camera,
                               CallbackInfo ci) {
        float scale = PvpVisuals.tagScale;
        if (scale != 1) pose.scale(scale, scale, scale);
    }

    @ModifyArg(method = "submitNameTag", at = @At(value = "INVOKE", target = NAME_TAG), index = 5)
    private int lads$fade(int color) {
        return PvpVisuals.alpha(color, PvpVisuals.tagAlpha);
    }
}
