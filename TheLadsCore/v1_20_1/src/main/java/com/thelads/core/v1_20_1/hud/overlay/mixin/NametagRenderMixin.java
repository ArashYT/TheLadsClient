package com.thelads.core.v1_20_1.hud.overlay.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thelads.core.client.ladsapi.LadsBadge;
import com.thelads.core.v1_20_1.hud.overlay.Nametags1201;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Nametags: renames, background, text shadow and Lads Client badge on every name tag (players' too: PlayerRenderer draws through this method). */
@Mixin(EntityRenderer.class)
public class NametagRenderMixin {
    private static final String DRAW = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I";
    @Unique private static final ResourceLocation LADS_BADGE = new ResourceLocation(LadsBadge.TEXTURE_NAMESPACE, LadsBadge.TEXTURE_PATH);
    @Unique private static int lads$lastBadgeTexId = -1;
    @Unique private Entity lads$tagEntity;

    /** While a name tag draws, Font puts its text in front of the shadow (NametagShadowDepthMixin). */
    @Inject(method = "renderNameTag", at = @At("HEAD"))
    private void lads$tagStart(Entity entity, Component component, PoseStack poseStack, MultiBufferSource multiBufferSource, int packedLight, CallbackInfo ci) {
        Nametags1201.drawing = true;
        lads$tagEntity = entity;
    }

    @Inject(method = "renderNameTag", at = @At("RETURN"))
    private void lads$tagEnd(CallbackInfo ci) {
        Nametags1201.drawing = false;
        lads$tagEntity = null;
    }

    @ModifyVariable(method = "renderNameTag", at = @At("HEAD"), argsOnly = true)
    private Component lads$rename(Component name) {
        return Nametags1201.rename(name);
    }

    @WrapOperation(method = "renderNameTag", at = @At(value = "INVOKE", target = DRAW))
    private int lads$draw(Font font, Component text, float x, float y, int color, boolean shadow, Matrix4f matrix,
                          MultiBufferSource buffers, Font.DisplayMode mode, int background, int light, Operation<Integer> original) {
        int bg = Nametags1201.background(background);
        boolean sh = Nametags1201.shadow(shadow, mode == Font.DisplayMode.NORMAL);
        Entity entity = lads$tagEntity;
        if (entity != null && entity.getUUID() != null && LadsBadge.showOnNametag(entity.getUUID())) {
            var tex = Minecraft.getInstance().getTextureManager().getTexture(LADS_BADGE);
            if (tex != null) {
                int id = tex.getId();
                if (id != lads$lastBadgeTexId && id != -1) {
                    lads$lastBadgeTexId = id;
                    tex.setFilter(true, false);
                }
            }

            float shift = (float) (LadsBadge.NAMETAG_SIZE + LadsBadge.GAP) / 2.0F;
            float textX = x + shift;
            float badgeX0 = textX - (float) (LadsBadge.NAMETAG_SIZE + LadsBadge.GAP);
            float badgeX1 = badgeX0 + (float) LadsBadge.NAMETAG_SIZE;
            float badgeY0 = y;
            float badgeY1 = y + (float) LadsBadge.NAMETAG_SIZE;

            if (bg != 0) {
                RenderType bgType = (mode == Font.DisplayMode.SEE_THROUGH)
                        ? RenderType.textBackgroundSeeThrough()
                        : RenderType.textBackground();
                VertexConsumer bgBuilder = buffers.getBuffer(bgType);
                float bgA = ((bg >> 24) & 0xFF) / 255.0F;
                float bgR = ((bg >> 16) & 0xFF) / 255.0F;
                float bgG = ((bg >> 8) & 0xFF) / 255.0F;
                float bgB = (bg & 0xFF) / 255.0F;
                bgBuilder.vertex(matrix, badgeX0 - 1.0F, badgeY0 - 1.0F, 0.0F).color(bgR, bgG, bgB, bgA).uv2(light).endVertex();
                bgBuilder.vertex(matrix, badgeX0 - 1.0F, badgeY1 + 1.0F, 0.0F).color(bgR, bgG, bgB, bgA).uv2(light).endVertex();
                bgBuilder.vertex(matrix, textX, badgeY1 + 1.0F, 0.0F).color(bgR, bgG, bgB, bgA).uv2(light).endVertex();
                bgBuilder.vertex(matrix, textX, badgeY0 - 1.0F, 0.0F).color(bgR, bgG, bgB, bgA).uv2(light).endVertex();
            }

            RenderType textType = (mode == Font.DisplayMode.SEE_THROUGH)
                    ? RenderType.textSeeThrough(LADS_BADGE)
                    : RenderType.text(LADS_BADGE);
            VertexConsumer badgeBuilder = buffers.getBuffer(textType);
            float alpha = ((color >> 24) & 0xFF) / 255.0F;
            if (alpha <= 0.0F) alpha = 1.0F;
            badgeBuilder.vertex(matrix, badgeX0, badgeY0, 0.0F).color(1.0F, 1.0F, 1.0F, alpha).uv(0.0F, 0.0F).uv2(light).endVertex();
            badgeBuilder.vertex(matrix, badgeX0, badgeY1, 0.0F).color(1.0F, 1.0F, 1.0F, alpha).uv(0.0F, 1.0F).uv2(light).endVertex();
            badgeBuilder.vertex(matrix, badgeX1, badgeY1, 0.0F).color(1.0F, 1.0F, 1.0F, alpha).uv(1.0F, 1.0F).uv2(light).endVertex();
            badgeBuilder.vertex(matrix, badgeX1, badgeY0, 0.0F).color(1.0F, 1.0F, 1.0F, alpha).uv(1.0F, 0.0F).uv2(light).endVertex();

            return original.call(font, text, textX, y, color, sh, matrix, buffers, mode, bg, light);
        }

        return original.call(font, text, x, y, color, sh, matrix, buffers, mode, bg, light);
    }
}
