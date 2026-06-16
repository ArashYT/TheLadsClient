package com.thelads.core.mixin.alwayson.skinlayers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thelads.core.features.alwayson.skinlayers.accessor.ModelPartInjector;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = {"traben.entity_model_features.models.parts.EMFModelPart"})
public abstract class EMFModelPartMixin implements ModelPartInjector {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V", at = @At("HEAD"), cancellable = true)
    public void render(PoseStack poseStack, VertexConsumer vertexConsumer, int light, int overlay, int color, CallbackInfo ci) {
        if (this.isVisible() && this.getInjectedMesh() != null) {
            poseStack.pushPose();
            this.prepareTranslateAndRotate(poseStack);
            this.getOffsetProvider().applyOffset(poseStack, this.getInjectedMesh());
            this.getInjectedMesh().render((ModelPart) (Object) this, poseStack, vertexConsumer, light, overlay, color);
            poseStack.popPose();
            ci.cancel();
        }
    }
}
