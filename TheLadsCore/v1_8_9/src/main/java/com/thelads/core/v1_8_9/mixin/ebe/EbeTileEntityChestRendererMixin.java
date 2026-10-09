package com.thelads.core.v1_8_9.mixin.ebe;

import com.thelads.core.client.ebe.BakedBlockEntities;
import net.minecraft.client.renderer.tileentity.TileEntityChestRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** -Dlads.ebe.xmas=true|false forces the Christmas chest texture for the vanilla renderer too (pixel-diff QA). */
@Mixin(TileEntityChestRenderer.class)
public abstract class EbeTileEntityChestRendererMixin {
    @Shadow private boolean isChristmas;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void lads$ebeForcedXmas(CallbackInfo ci) {
        if (BakedBlockEntities.christmasForced()) isChristmas = BakedBlockEntities.christmas();
    }
}
