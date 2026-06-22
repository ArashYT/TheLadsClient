package com.thelads.core.mixin.alwayson.skinlayers;

import com.mojang.authlib.GameProfile;
import com.thelads.core.features.alwayson.skinlayers.SkinUtil;
import com.thelads.core.features.alwayson.skinlayers.SkullRendererCache;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.special.PlayerHeadSpecialRenderer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerHeadSpecialRenderer.class)
public class BlockEntityWithoutLevelRendererMixin {
    @Inject(method = "extractArgument", at = @At("HEAD"))
    public void extractArgument(ItemStack itemStack, CallbackInfoReturnable<PlayerSkinRenderCache.RenderInfo> cir) {
        if (!com.thelads.core.config.ModuleManager.getInstance().getModule("SkinLayers").isEnabled()) {
            SkullRendererCache.renderNext = false;
            SkullRendererCache.lastSkull = null;
            return;
        }
        ResolvableProfile profileComponent = itemStack.get(net.minecraft.core.component.DataComponents.PROFILE);
        GameProfile profile = profileComponent != null ? profileComponent.partialProfile() : null;
        if (profile != null) {
            SkullRendererCache.lastSkull = SkullRendererCache.itemCache.computeIfAbsent(profile, it -> new SkullRendererCache.ItemSettings());
            if (!SkullRendererCache.lastSkull.initialized() && SkullRendererCache.lastSkull.getHeadLayers() == null) {
                SkinUtil.setup3dLayers(profile, SkullRendererCache.lastSkull);
            }
            SkullRendererCache.renderNext = SkullRendererCache.lastSkull.getHeadLayers() != null;
        }
    }
}
