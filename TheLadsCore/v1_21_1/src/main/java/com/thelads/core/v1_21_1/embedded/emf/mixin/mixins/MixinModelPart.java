package com.thelads.core.v1_21_1.embedded.emf.mixin.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v1_21_1.embedded.emf.models.IEMFModelNameContainer;
import com.thelads.core.v1_21_1.embedded.emf.EMFManager;
import com.thelads.core.v1_21_1.embedded.emf.models.animation.state.EMFState;
import com.thelads.core.v1_21_1.embedded.emf.utils.IEMFTextureSizeSupplier;
import com.thelads.core.v1_21_1.embedded.emf.models.EMFModel_ID;

import java.util.Map;

@Mixin(ModelPart.class)
public class MixinModelPart implements IEMFModelNameContainer, IEMFTextureSizeSupplier {
    @Shadow
    public Map<String, ModelPart> children;
    @Unique
    EMFModel_ID emf$modelInfo = null;
    @Unique
    private int[] emf$textureSize = null;

    @Inject(method =
                "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
            at = @At(value = "HEAD"))
    private void emf$injectAnnouncerPart(final PoseStack poseStack, final VertexConsumer vertexConsumer, final int i, final int j,
                                         final int k
            , final CallbackInfo ci) {
        if (EMFState.announceModels && emf$modelInfo != null) {
            EMFManager.getInstance().modelsAnnounced.add(emf$modelInfo);
        }
    }


    @Override
    public void emf$insertKnownMappings(EMFModel_ID newName) {
        emf$modelInfo = newName;
        children.values().forEach(
                (part) -> ((IEMFModelNameContainer) part).emf$insertKnownMappings(newName));
    }

    @Override
    public int[] emf$getTextureSize() {
        return emf$textureSize;
    }

    @Override
    public void emf$setTextureSize(final int[] size) {
        emf$textureSize = size;
    }
}