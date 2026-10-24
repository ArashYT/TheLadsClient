package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.cosmetics.wearables.CosmeticsLayer189;
import com.thelads.core.v1_8_9.feature.SkinLayers189;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** SkinLayers off: 3D Skin Layers hides vanilla's flat outer layers here while it draws its own, so they are shown again. */
@Mixin(RenderPlayer.class)
public abstract class RenderPlayerMixin {
    @Inject(method = "<init>(Lnet/minecraft/client/renderer/entity/RenderManager;Z)V", at = @At("RETURN"))
    private void ladsAddCosmeticsLayer(RenderManager renderManager, boolean useSmallArms, CallbackInfo ci) {
        ((RenderPlayer) (Object) this).addLayer(new CosmeticsLayer189((RenderPlayer) (Object) this));
    }

    @Inject(method = "setModelVisibilities", at = @At("RETURN"), require = 1)
    private void ladsFlatLayers(AbstractClientPlayer player, CallbackInfo ci) {
        if (!SkinLayers189.flatLayers()) return;
        ModelPlayer model = ((RenderPlayer) (Object) this).getMainModel();
        model.bipedHeadwear.isHidden = model.bipedBodyWear.isHidden = model.bipedLeftArmwear.isHidden = model.bipedRightArmwear.isHidden
            = model.bipedLeftLegwear.isHidden = model.bipedRightLegwear.isHidden = false;
    }
}
