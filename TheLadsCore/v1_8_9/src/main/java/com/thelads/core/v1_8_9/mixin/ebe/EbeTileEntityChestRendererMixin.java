package com.thelads.core.v1_8_9.mixin.ebe;

import com.thelads.core.client.ebe.BakedBlockEntities;
import com.thelads.core.client.hypixel.OpenedChestHighlight;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntityChestRenderer;
import net.minecraft.tileentity.TileEntityChest;
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

    @Inject(method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntityChest;DDDFI)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelChest;renderAll()V"),
            require = 0)
    private void lads$tintOpenedChest(TileEntityChest te, double x, double y, double z, float partialTicks, int destroyStage, CallbackInfo ci) {
        if (te != null && te.getPos() != null && OpenedChestHighlight.isOpened(te.getPos().getX(), te.getPos().getY(), te.getPos().getZ())) {
            GlStateManager.color(0.3F, 1.0F, 0.3F, 1.0F);
        }
    }
}
