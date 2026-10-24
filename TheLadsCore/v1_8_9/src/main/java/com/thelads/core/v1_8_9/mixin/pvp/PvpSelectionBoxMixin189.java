package com.thelads.core.v1_8_9.mixin.pvp;

import com.thelads.core.v1_8_9.feature.PvpVisuals189;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.AxisAlignedBB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Block Overlay: the targeted block's outline in the module's colour and width, and its fill (PvpVisuals189.selection); on return
 * the line width and colour are what vanilla (and OptiFine M5, the same calls) leave.
 */
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

    @Redirect(method = "drawSelectionBox", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;color(FFFF)V"), require = 0)
    private void lads$vanillaColor(float red, float green, float blue, float alpha) {
        PvpVisuals189.vanillaColor(red, green, blue, alpha);
    }

    /** The overlay's line width and colours do not outlive the call: vanilla's own are back (PvpVisuals189.selectionDone). */
    @Inject(method = "drawSelectionBox", at = @At("RETURN"), require = 0)
    private void lads$restore(EntityPlayer player, MovingObjectPosition target, int execute, float partial, CallbackInfo ci) {
        PvpVisuals189.selectionDone();
    }
}
