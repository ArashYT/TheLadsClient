package com.thelads.core.v1_8_9.mixin.optifine;

import com.thelads.core.modules.OptiFine189Module;
import net.minecraft.client.renderer.entity.RenderEntityItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * OptiFine Dropped Items setting:
 * 0=Default, 1=Fast (suppresses item rotation and item spreading into multiple entities), 2=Fancy.
 */
@Mixin(RenderEntityItem.class)
public abstract class RenderEntityItemOfMixin189 {
    @Inject(method = "shouldSpreadItems", at = @At("HEAD"), cancellable = true, remap = false)
    private void lads$fastSpreadItems(CallbackInfoReturnable<Boolean> cir) {
        if (OptiFine189Module.sOfDroppedItems == 1) { // Fast
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "shouldBob", at = @At("HEAD"), cancellable = true, remap = false)
    private void lads$fastBob(CallbackInfoReturnable<Boolean> cir) {
        if (OptiFine189Module.sOfDroppedItems == 1) { // Fast
            cir.setReturnValue(false);
        }
    }
}
