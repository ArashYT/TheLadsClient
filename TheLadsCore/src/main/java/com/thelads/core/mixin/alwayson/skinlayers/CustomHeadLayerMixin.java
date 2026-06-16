package com.thelads.core.mixin.alwayson.skinlayers;

import com.mojang.authlib.GameProfile;
import com.thelads.core.features.alwayson.skinlayers.SkinLayersModBase;
import com.thelads.core.features.alwayson.skinlayers.SkinUtil;
import com.thelads.core.features.alwayson.skinlayers.SkullRendererCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.SkullBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CustomHeadLayer.class)
public class CustomHeadLayerMixin<T extends LivingEntity, M extends EntityModel> {
    @Inject(method = "resolveSkullRenderType", at = @At("HEAD"))
    private void resolveSkullRenderType(LivingEntityRenderState livingEntityRenderState, SkullBlock.Type type, CallbackInfoReturnable<RenderType> ci) {
        boolean inGui = Minecraft.getInstance().screen != null;
        if (!inGui && Minecraft.getInstance().player != null && Minecraft.getInstance().gameRenderer.getMainCamera().position().distanceToSqr(livingEntityRenderState.x, livingEntityRenderState.y, livingEntityRenderState.z) > (double)(SkinLayersModBase.config.renderDistanceLOD * SkinLayersModBase.config.renderDistanceLOD)) {
            return;
        }
        if (!(livingEntityRenderState.headItem.isEmpty() && livingEntityRenderState.wornHeadType == null || livingEntityRenderState.wornHeadProfile == null)) {
            ResolvableProfile wornHeadProfile = (ResolvableProfile) livingEntityRenderState.wornHeadProfile;
            GameProfile gameProfile = wornHeadProfile != null ? wornHeadProfile.partialProfile() : null;
            if (gameProfile != null) {
                SkullRendererCache.lastSkull = SkullRendererCache.itemCache.computeIfAbsent(gameProfile, it -> new SkullRendererCache.ItemSettings());
                if (!SkullRendererCache.lastSkull.initialized() && SkullRendererCache.lastSkull.getHeadLayers() == null) {
                    SkinUtil.setup3dLayers(gameProfile, SkullRendererCache.lastSkull);
                }
                SkullRendererCache.renderNext = SkullRendererCache.lastSkull.getHeadLayers() != null;
            }
        }
    }
}
