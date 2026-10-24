package com.thelads.core.v26_2.feature.pvp.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.v26_2.feature.pvp.PvpVisuals26;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Block Overlay: the main outline (submitHitOutline's second call; the first is High Contrast's wide backing line, left as it is)
 * in the module's colour and width, then the fill over the block.
 */
@Mixin(LevelRenderer.class)
public class PvpBlockOverlayMixin26 {
    private static final String HIT_OUTLINE = "Lnet/minecraft/client/renderer/LevelRenderer;submitHitOutline(Lcom/mojang/blaze3d/vertex/PoseStack;"
        + "Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/rendertype/RenderType;"
        + "Lnet/minecraft/client/renderer/state/level/BlockOutlineRenderState;IFZ)V";

    @ModifyArg(method = "submitBlockOutline", at = @At(value = "INVOKE", target = HIT_OUTLINE, ordinal = 1), index = 4)
    private int lads$outlineColor(int color) {
        return PvpVisuals26.outlineColor(color);
    }

    @ModifyArg(method = "submitBlockOutline", at = @At(value = "INVOKE", target = HIT_OUTLINE, ordinal = 1), index = 5)
    private float lads$outlineWidth(float width) {
        return PvpVisuals26.outlineWidth(width);
    }

    @Inject(method = "submitBlockOutline", at = @At("TAIL"))
    private void lads$fill(PoseStack pose, SubmitNodeCollector collector, LevelRenderState level, CallbackInfo ci) {
        PvpVisuals26.fill(pose, collector, level);
    }
}
