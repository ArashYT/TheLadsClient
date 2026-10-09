package com.thelads.core.v1_20_1.mixin.ebe;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.v1_20_1.feature.ebe.BakedBlockEntities1201;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Baked block entities skip their renderer while the chunk mesh shows them (vanilla, Sodium and Embeddium render through here). */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class EbeBlockEntityRenderDispatcherMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void lads$ebeSkipBaked(BlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffers, CallbackInfo ci) {
        if (BakedBlockEntities1201.skipRenderer(blockEntity)) ci.cancel();
    }
}
