package com.thelads.core.v1_8_9.mixin.optifine;

import com.thelads.core.modules.OptiFine189Module;
import net.minecraft.block.BlockLeaves;
import net.minecraft.util.EnumWorldBlockLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * OptiFine Trees setting: controls leaves opacity and rendering pass independently of global Graphics.
 * Values: 0=Default, 1=Fast (opaque), 2=Fancy (cutout), 3=Smart (cutout).
 */
@Mixin(BlockLeaves.class)
public abstract class BlockLeavesMixin189 {
    @Inject(method = "isOpaqueCube", at = @At("HEAD"), cancellable = true)
    private void lads$treesOpaque(CallbackInfoReturnable<Boolean> cir) {
        int trees = OptiFine189Module.sOfTrees;
        if (trees == 1) { // Fast
            cir.setReturnValue(true);
        } else if (trees == 2 || trees == 3) { // Fancy / Smart
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "getBlockLayer", at = @At("HEAD"), cancellable = true)
    private void lads$treesBlockLayer(CallbackInfoReturnable<EnumWorldBlockLayer> cir) {
        int trees = OptiFine189Module.sOfTrees;
        if (trees == 1) { // Fast
            cir.setReturnValue(EnumWorldBlockLayer.SOLID);
        } else if (trees == 2 || trees == 3) { // Fancy / Smart
            cir.setReturnValue(EnumWorldBlockLayer.CUTOUT_MIPPED);
        }
    }
}
