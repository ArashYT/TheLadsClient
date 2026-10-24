package com.thelads.core.v1_20_1.hud.overlay.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.client.PvpVisuals;
import com.thelads.core.v1_20_1.hud.overlay.Nametags1201;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Nametags: renames, background and text shadow on every name tag (players' too: PlayerRenderer draws through this method). */
@Mixin(EntityRenderer.class)
public class NametagRenderMixin {
    private static final String DRAW = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I";
    @Shadow @Final protected EntityRenderDispatcher entityRenderDispatcher;

    /**
     * While a name tag draws, Font puts its text in front of the shadow (NametagShadowDepthMixin). Players' tags get Scale and
     * Distance Fade (PvpVisuals.tagStart).
     */
    @Inject(method = "renderNameTag", at = @At("HEAD"))
    private void lads$tagStart(Entity entity, Component name, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        Nametags1201.drawing = true;
        PvpVisuals.tagStart(entity instanceof Player, entityRenderDispatcher.distanceToSqr(entity));
    }

    @Inject(method = "renderNameTag", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V", shift = At.Shift.AFTER))
    private void lads$tagScale(Entity entity, Component name, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        float scale = PvpVisuals.tagScale;
        if (scale != 1) pose.scale(scale, scale, scale);
    }

    /** Show Ping and Nametag Health after the name, with the normal (depth-tested) pass. */
    @Inject(method = "renderNameTag", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"))
    private void lads$tagExtras(Entity entity, Component name, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        Nametags1201.extras(entity, name, pose.last().pose(), buffers, light);
    }

    /** Both passes of the text (see-through, then normal) fade with Distance Fade. */
    @ModifyArg(method = "renderNameTag", at = @At(value = "INVOKE", target = DRAW), index = 3)
    private int lads$fade(int color) {
        return PvpVisuals.alpha(color, PvpVisuals.tagAlpha);
    }

    @Inject(method = "renderNameTag", at = @At("RETURN"))
    private void lads$tagEnd(CallbackInfo ci) {
        Nametags1201.drawing = false;
    }

    @ModifyVariable(method = "renderNameTag", at = @At("HEAD"), argsOnly = true)
    private Component lads$rename(Component name) {
        return Nametags1201.rename(name);
    }

    @ModifyArg(method = "renderNameTag", at = @At(value = "INVOKE", target = DRAW), index = 8)
    private int lads$background(int vanillaColor) {
        int color = Nametags1201.background(vanillaColor);
        return color == 0 ? 0 : Math.round(PvpVisuals.background((color >>> 24) / 255f) * 255) << 24;
    }

    @ModifyArg(method = "renderNameTag", at = @At(value = "INVOKE", target = DRAW), index = 4)
    private boolean lads$shadow(Component text, float x, float y, int color, boolean shadow, Matrix4f matrix, MultiBufferSource buffers,
                                Font.DisplayMode mode, int background, int light) {
        return Nametags1201.shadow(shadow, mode == Font.DisplayMode.NORMAL);
    }
}
