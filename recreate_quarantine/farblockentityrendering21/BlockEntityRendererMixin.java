package com.thelads.core.mixin.auto.farblockentityrendering21;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRenderer.class)
public abstract class BlockEntityRendererMixin<T extends BlockEntity> {

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void onRender(T blockEntity, float partialTicks, int destroyStage, PoseStack poseStack, MultiBufferSource bufferSource, int combinedLightIn, CallbackInfo ci) {
        // Custom rendering logic can be added here
    }
}
