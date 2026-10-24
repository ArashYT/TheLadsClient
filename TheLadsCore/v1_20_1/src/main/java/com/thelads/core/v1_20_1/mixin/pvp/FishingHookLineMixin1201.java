package com.thelads.core.v1_20_1.mixin.pvp;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thelads.core.client.PvpVisuals;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Render Tweaks, Hide Fishing Line: the line's vertices are skipped; the bobber stays. */
@Mixin(FishingHookRenderer.class)
public class FishingHookLineMixin1201 {
    @WrapWithCondition(method = "render(Lnet/minecraft/world/entity/projectile/FishingHook;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/FishingHookRenderer;stringVertex(FFFLcom/mojang/blaze3d/vertex/VertexConsumer;Lcom/mojang/blaze3d/vertex/PoseStack$Pose;FF)V"))
    private boolean lads$line(float x, float y, float z, VertexConsumer buffer, PoseStack.Pose pose, float from, float to) {
        return !PvpVisuals.hideFishingLine;
    }
}
