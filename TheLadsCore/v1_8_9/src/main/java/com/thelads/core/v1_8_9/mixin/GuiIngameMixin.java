package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.NativeHud;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.renderer.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * SmoothHotbar: renderTooltip's second texture draw, the selected-slot frame, glides to the slot (NativeHud.selectionOffset).
 * Raised: vanilla draws 22 of the frame's 24 rows; off the bottom edge the last two show, so all 24 are drawn.
 */
@Mixin(GuiIngame.class)
public abstract class GuiIngameMixin {
    @Redirect(method = "renderTooltip", at = @At(value = "INVOKE", ordinal = 1,
        target = "Lnet/minecraft/client/gui/GuiIngame;drawTexturedModalRect(IIIIII)V"), require = 1)
    private void ladsSelection(GuiIngame gui, int x, int y, int u, int v, int width, int height) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(NativeHud.selectionOffset(x), 0, 0);
        gui.drawTexturedModalRect(x, y, u, v, width, com.thelads.core.v1_8_9.feature.Raised189.hotbar() > 0 ? 24 : height);
        GlStateManager.popMatrix();
    }
}
