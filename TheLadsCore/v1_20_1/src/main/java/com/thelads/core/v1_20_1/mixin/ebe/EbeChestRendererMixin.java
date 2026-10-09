package com.thelads.core.v1_20_1.mixin.ebe;

import com.thelads.core.client.ebe.BakedBlockEntities;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** -Dlads.ebe.xmas=true|false forces the Christmas chest texture for the vanilla renderer too (pixel-diff QA). */
@Mixin(ChestRenderer.class)
public abstract class EbeChestRendererMixin {
    @Shadow private boolean xmasTextures;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void lads$ebeForcedXmas(BlockEntityRendererProvider.Context context, CallbackInfo ci) {
        if (BakedBlockEntities.christmasForced()) xmasTextures = BakedBlockEntities.christmas();
    }
}
