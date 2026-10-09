package com.thelads.core.v1_8_9.mixin.ebe;

import com.thelads.core.v1_8_9.feature.ebe.BakedBlockEntities189;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Signs report render type 2 while baking runs, so the chunk builder hands them to renderBlock (vanilla: -1, skipped). */
@Mixin(BlockContainer.class)
public abstract class EbeBlockContainerMixin {
    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void lads$ebeSignRenderType(CallbackInfoReturnable<Integer> cir) {
        if (BakedBlockEntities189.meshedSign((Block) (Object) this)) cir.setReturnValue(2);
    }
}
