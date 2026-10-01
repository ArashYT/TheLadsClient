package com.thelads.core.v1_21_11.embedded.emf.mixin.mixins;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.PartDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.thelads.core.v1_21_11.embedded.emf.EMF;
import com.thelads.core.v1_21_11.embedded.emf.utils.IEMFTextureSizeSupplier;

@Mixin(PartDefinition.class)
public class MixinModelPartData {
    @Inject(method = "bake",
            at = @At(value = "RETURN"))
    private void emf$injectAnnouncerData(final int textureWidth, final int textureHeight, final CallbackInfoReturnable<ModelPart> cir) {
        if (cir.getReturnValue() instanceof IEMFTextureSizeSupplier) {
            ((IEMFTextureSizeSupplier) cir.getReturnValue()).emf$setTextureSize(new int[]{textureWidth, textureHeight});
        }

    }
}