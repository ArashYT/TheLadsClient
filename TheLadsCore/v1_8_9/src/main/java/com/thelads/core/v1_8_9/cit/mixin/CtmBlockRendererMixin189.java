package com.thelads.core.v1_8_9.cit.mixin;

import com.thelads.core.v1_8_9.cit.Ctm189;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.util.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Connected Textures (CTM) block model renderer hook for 1.8.9 on Ionium.
 */
@Mixin(BlockModelRenderer.class)
public abstract class CtmBlockRendererMixin189 {
    @Inject(method = "renderModel(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/client/resources/model/IBakedModel;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/BlockPos;Lnet/minecraft/client/renderer/WorldRenderer;Z)Z",
            at = @At("HEAD"), require = 0)
    private void lads$ctmRender(IBlockAccess world, IBakedModel model, IBlockState state, BlockPos pos,
                                WorldRenderer renderer, boolean checkSides, CallbackInfoReturnable<Boolean> cir) {
        if (Ctm189.getRuleCount() > 0) {
            // CTM active in block model renderer
        }
    }
}
