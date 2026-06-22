package com.thelads.core.mixin;

import com.thelads.core.config.ModuleManager;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntityRenderer.class)
public interface FarBlockEntitiesMixin {

    @Inject(method = "getViewDistance", at = @At("RETURN"), cancellable = true, require = 0)
    default void onGetViewDistance(CallbackInfoReturnable<Integer> cir) {
        if (ModuleManager.getInstance().getModule("FarBlockEntities").isEnabled()) {
            cir.setReturnValue(cir.getReturnValue() * 4);
        }
    }
}
