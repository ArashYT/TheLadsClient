package com.thelads.core.v26_2.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import com.thelads.core.v26_2.feature.ShulkerContents;
import com.thelads.core.v26_2.feature.ShulkerIconState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.ShulkerBoxRenderer;
import net.minecraft.client.renderer.blockentity.state.ShulkerBoxRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ShulkerBoxRenderer.class)
public class ShulkerIconRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/level/block/entity/ShulkerBoxBlockEntity;Lnet/minecraft/client/renderer/blockentity/state/ShulkerBoxRenderState;FLnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",
        at = @At("TAIL"), require = 1)
    private void lads$extract(ShulkerBoxBlockEntity box, ShulkerBoxRenderState state, float delta, Vec3 camera,
                              ModelFeatureRenderer.CrumblingOverlay breaking, CallbackInfo callback) {
        ShulkerIconState icon = (ShulkerIconState) state;
        icon.lads$shulkerIcon().clear();
        Minecraft minecraft = Minecraft.getInstance();
        if (!NativeQualityOfLife.enabled("ShulkerBoxUtils") || !NativeQualityOfLife.bool("ShulkerBoxUtils", "World Icon", true) || minecraft.gui.hud.isHidden()
            || !Vec3.atCenterOf(box.getBlockPos()).closerThan(camera, NativeQualityOfLife.number("ShulkerBoxUtils", "Distance", 24))) return;
        ItemStack first = ShulkerContents.resolve(box);
        if (first.isEmpty()) return;
        minecraft.getItemModelResolver().updateForTopItem(icon.lads$shulkerIcon(), first, ItemDisplayContext.FIXED,
            box.getLevel(), null, box.getBlockPos().hashCode());
        float height = 1 + (float) NativeQualityOfLife.number("ShulkerBoxUtils", "Height", 35) / 100;
        if (state.direction == Direction.UP) height += state.progress * .5f;
        if (NativeQualityOfLife.bool("ShulkerBoxUtils", "Animate", true) && minecraft.options.screenEffectScale().get() > 0
            && box.getLevel() != null) height += (float) Math.sin((box.getLevel().getGameTime() + delta) * .12) * .035f;
        icon.lads$iconTransform((float) NativeQualityOfLife.number("ShulkerBoxUtils", "Icon Size", 65) / 100, height);
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/blockentity/state/ShulkerBoxRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At("TAIL"), require = 1)
    private void lads$submit(ShulkerBoxRenderState state, PoseStack pose, SubmitNodeCollector collector,
                            CameraRenderState camera, CallbackInfo callback) {
        ShulkerIconState icon = (ShulkerIconState) state;
        if (icon.lads$shulkerIcon().isEmpty()) return;
        pose.pushPose();
        try {
            pose.translate(.5, icon.lads$iconHeight(), .5);
            pose.mulPose(camera.orientation);
            pose.scale(icon.lads$iconScale(), icon.lads$iconScale(), icon.lads$iconScale());
            icon.lads$shulkerIcon().submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        } finally { pose.popPose(); }
    }
}
