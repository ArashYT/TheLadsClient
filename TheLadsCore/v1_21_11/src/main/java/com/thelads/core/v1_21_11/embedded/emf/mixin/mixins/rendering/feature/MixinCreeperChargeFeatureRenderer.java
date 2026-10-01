package com.thelads.core.v1_21_11.embedded.emf.mixin.mixins.rendering.feature;


import net.minecraft.client.model.monster.creeper.CreeperModel;
import net.minecraft.client.renderer.entity.layers.CreeperPowerLayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.thelads.core.v1_21_11.embedded.emf.models.parts.EMFModelPartRoot;
import com.thelads.core.v1_21_11.embedded.emf.models.IEMFModel;


@Mixin(CreeperPowerLayer.class)
public abstract class MixinCreeperChargeFeatureRenderer {


    @Shadow
    @Final
    private
        CreeperModel
            model;

    @Inject(
            method = "getTextureLocation",
            at = @At(value = "RETURN"), cancellable = true)
    private void emf$getTextureRedirect(CallbackInfoReturnable<Identifier> cir) {
        if (model != null && ((IEMFModel) model).emf$isEMFModel()) {
            EMFModelPartRoot root = ((IEMFModel) model).emf$getEMFRootModel();
            if (root != null) {
                Identifier texture = root.getTopLevelJemTexture();
                if (texture != null) {
                    cir.setReturnValue(texture);
                }
            }
        }
    }


}
