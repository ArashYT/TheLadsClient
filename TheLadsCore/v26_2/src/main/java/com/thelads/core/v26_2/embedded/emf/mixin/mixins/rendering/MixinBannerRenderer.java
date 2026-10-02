package com.thelads.core.v26_2.embedded.emf.mixin.mixins.rendering;

import net.minecraft.client.renderer.blockentity.BannerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v26_2.embedded.emf.EMFManager;

@Mixin(BannerRenderer.class)
public abstract class MixinBannerRenderer {

    @Inject(method =
            "submitPatterns",
            at = @At("HEAD"))
    private static void emf$injected(final CallbackInfo ci) {
        EMFManager.getInstance().entityRenderCount++;
    }

}
