package com.thelads.core.v1_8_9.mixin.pvp;

import com.thelads.core.v1_8_9.feature.PvpVisuals189;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.util.AxisAlignedBB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Block Overlay: the targeted block's outline in the module's colour and width, and its fill (PvpVisuals189.selection). */
@Mixin(RenderGlobal.class)
public class PvpSelectionBoxMixin189 {
    @ModifyArg(method = "drawSelectionBox", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glLineWidth(F)V", remap = false), require = 0)
    private float lads$lineWidth(float width) {
        return PvpVisuals189.lineWidth(width);
    }

    @Redirect(method = "drawSelectionBox", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/RenderGlobal;drawSelectionBoundingBox(Lnet/minecraft/util/AxisAlignedBB;)V"), require = 0)
    private void lads$selection(AxisAlignedBB box) {
        PvpVisuals189.selection(box);
    }
}
