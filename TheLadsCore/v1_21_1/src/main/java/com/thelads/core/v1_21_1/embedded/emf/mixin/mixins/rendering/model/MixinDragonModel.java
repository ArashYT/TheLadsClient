package com.thelads.core.v1_21_1.embedded.emf.mixin.mixins.rendering.model;


import org.spongepowered.asm.mixin.Mixin;


import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v1_21_1.embedded.emf.EMFManager;
import net.minecraft.client.renderer.entity.EnderDragonRenderer;
@Mixin(EnderDragonRenderer.DragonModel.class)
public abstract class MixinDragonModel {
    @Inject(method = "renderToBuffer",
            at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
                    shift = At.Shift.BEFORE))
    private void emf$allowMultiPartRender(final CallbackInfo ci) {
        EMFManager.getInstance().entityRenderCount++;
    }
    @Inject(method = "renderToBuffer",
            at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EnderDragonRenderer$DragonModel;renderSide(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFLnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;I)V",
            shift = At.Shift.BEFORE, ordinal = 0))
    private void emf$allowMultiPartRender2(final CallbackInfo ci) {
        EMFManager.getInstance().entityRenderCount++;
    }
}
