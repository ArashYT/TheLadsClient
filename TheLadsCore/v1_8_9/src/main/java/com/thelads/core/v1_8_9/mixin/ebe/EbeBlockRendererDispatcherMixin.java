package com.thelads.core.v1_8_9.mixin.ebe;

import com.thelads.core.v1_8_9.feature.ebe.BakedBlockEntities189;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.util.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Chunk meshing: baked chests and sign boards are drawn as block models here (the position is known). */
@Mixin(BlockRendererDispatcher.class)
public abstract class EbeBlockRendererDispatcherMixin {
    @Inject(method = "renderBlock", at = @At("HEAD"), cancellable = true)
    private void lads$ebeRenderBaked(IBlockState state, BlockPos pos, IBlockAccess world, WorldRenderer renderer, CallbackInfoReturnable<Boolean> cir) {
        IBakedModel model = BakedBlockEntities189.model(state, pos, world);
        if (model == null) return;
        cir.setReturnValue(model != BakedBlockEntities189.NOTHING
            && ((BlockRendererDispatcher) (Object) this).getBlockModelRenderer().renderModel(world, model, state, pos, renderer));
    }
}
