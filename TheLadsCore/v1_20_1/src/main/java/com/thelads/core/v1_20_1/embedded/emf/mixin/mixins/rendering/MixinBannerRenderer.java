package com.thelads.core.v1_20_1.embedded.emf.mixin.mixins.rendering;

import net.minecraft.client.renderer.blockentity.BannerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v1_20_1.embedded.emf.EMFManager;

@Mixin(BannerRenderer.class)
public abstract class MixinBannerRenderer {

    @Inject(method =
            "renderPatterns(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/resources/model/Material;ZLjava/util/List;Z)V",
            at = @At("HEAD"))
    private static void emf$injected(final CallbackInfo ci) {
        EMFManager.getInstance().entityRenderCount++;
    }

}
