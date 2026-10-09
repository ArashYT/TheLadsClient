package com.thelads.core.v26_2.mixin.ebe;

import com.thelads.core.client.ebe.BakedBlockEntities;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** -Dlads.ebe.xmas=true|false forces the Christmas chest texture for the vanilla renderer too (pixel-diff QA). */
@Mixin(ChestRenderer.class)
public abstract class EbeChestRendererMixin {
    @Inject(method = "xmasTextures", at = @At("HEAD"), cancellable = true)
    private static void lads$ebeForcedXmas(CallbackInfoReturnable<Boolean> cir) {
        if (BakedBlockEntities.christmasForced()) cir.setReturnValue(BakedBlockEntities.christmas());
    }
}
