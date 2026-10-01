package com.thelads.core.v1_21_1.embedded.emf.mixin.mixins.rendering;

import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v1_21_1.embedded.emf.models.animation.state.EMFState;

@Mixin(ItemFrameRenderer.class)
public class MixinItemFrameEntityRenderer {

    @Inject(method =
            "render(Lnet/minecraft/world/entity/decoration/ItemFrame;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target =
                            "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
                    shift = At.Shift.AFTER))
    private void emf$setFrame(final CallbackInfo ci) {
        //basically "HEAD"
        EMFState.isInItemFrame = true;
    }
    
    @Inject(method =
            "render(Lnet/minecraft/world/entity/decoration/ItemFrame;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "TAIL"))
    private void emf$unsetFrame(final CallbackInfo ci) {
        EMFState.isInItemFrame = false;
    }
}