package com.thelads.core.v1_20_1.mixin.ebe;

import com.thelads.core.v1_20_1.feature.ebe.BakedBlockEntities1201;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Baked block entity states are meshed as models; vanilla, Sodium and Embeddium all call {@code getSeed(pos)} right before
 * reading a block's quads, which tells the baked model which position it is drawing.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class EbeBlockStateMixin implements BakedBlockEntities1201.Flag {
    @Unique private BakedModel lads$model;

    @Override public boolean lads$baked() { return lads$model != null; }

    @Override public BakedModel lads$model() { return lads$model; }

    @Override public void lads$setModel(BakedModel model) { lads$model = model; }

    @Inject(method = "getRenderShape", at = @At("HEAD"), cancellable = true)
    private void lads$ebeShape(CallbackInfoReturnable<RenderShape> cir) {
        if (lads$model != null) cir.setReturnValue(RenderShape.MODEL);
    }

    @Inject(method = "getSeed", at = @At("HEAD"))
    private void lads$ebeMeshPos(BlockPos pos, CallbackInfoReturnable<Long> cir) {
        if (lads$model != null) BakedBlockEntities1201.MESH_POS.get()[0] = pos.asLong();
    }
}
