package com.thelads.core.v1_20_1.mixin.ebe;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.v1_20_1.feature.ebe.BakedBlockEntities1201;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Single blocks drawn outside the world (minecart contents) keep vanilla's item-style renderer for baked states. */
@Mixin(BlockRenderDispatcher.class)
public abstract class EbeBlockRenderDispatcherMixin {
    @Shadow @Final private BlockEntityWithoutLevelRenderer blockEntityRenderer;

    @SuppressWarnings("deprecation")
    @Inject(method = "renderSingleBlock", at = @At("HEAD"), cancellable = true)
    private void lads$ebeSingleBlock(BlockState state, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay, CallbackInfo ci) {
        if (!((BakedBlockEntities1201.Flag) state).lads$baked()) return;
        RenderShape vanilla = state.getBlock().getRenderShape(state);
        if (vanilla == RenderShape.MODEL) return; // the bell: its own model path
        ci.cancel();
        if (vanilla == RenderShape.ENTITYBLOCK_ANIMATED)
            blockEntityRenderer.renderByItem(new ItemStack(state.getBlock()), ItemDisplayContext.NONE, poseStack, buffers, light, overlay);
    }
}
