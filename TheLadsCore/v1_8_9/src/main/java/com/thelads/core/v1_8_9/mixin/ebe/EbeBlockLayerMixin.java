package com.thelads.core.v1_8_9.mixin.ebe;

import com.thelads.core.v1_8_9.feature.ebe.BakedBlockEntities189;
import net.minecraft.block.Block;
import net.minecraft.util.EnumWorldBlockLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Baked chests and signs mesh in the cutout layer: alpha-tested and unmipmapped, like their tile entity renderers. */
@Mixin(Block.class)
public abstract class EbeBlockLayerMixin {
    @Inject(method = "getBlockLayer", at = @At("HEAD"), cancellable = true)
    private void lads$ebeLayer(CallbackInfoReturnable<EnumWorldBlockLayer> cir) {
        EnumWorldBlockLayer layer = BakedBlockEntities189.layer((Block) (Object) this);
        if (layer != null) cir.setReturnValue(layer);
    }
}
