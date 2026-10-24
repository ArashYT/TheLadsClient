package com.thelads.core.v1_8_9.mixin.pvp;

import com.thelads.core.v1_8_9.feature.BedColors189;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.util.EnumFacing;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BakedQuad.class)
public abstract class BakedQuadBedMixin189 {
    @Shadow @Final protected EnumFacing face;

    @Inject(method = "hasTintIndex", at = @At("HEAD"), cancellable = true)
    private void ladsBedHasTintIndex(CallbackInfoReturnable<Boolean> cir) {
        if (BedColors189.isRenderingBed() && this.face != EnumFacing.DOWN) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getTintIndex", at = @At("HEAD"), cancellable = true)
    private void ladsBedGetTintIndex(CallbackInfoReturnable<Integer> cir) {
        if (BedColors189.isRenderingBed() && this.face != EnumFacing.DOWN) {
            cir.setReturnValue(0);
        }
    }
}
