package com.thelads.core.v1_21_11.mixin;

import com.thelads.core.client.ServerListDrag;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * This version's GUI has no global alpha: every element carries its own colour. While the dragged server row is drawn (ServerListDrag.ghost)
 * the colours of what it draws, text, fills and blits, are made translucent where they all pass through.
 */
@Mixin(GuiGraphics.class)
abstract class GhostGraphicsMixin {
    @ModifyVariable(method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private int ladsGhostText(int color) {
        return ServerListDrag.ghostColor(color);
    }

    @ModifyVariable(method = "submitColoredRectangle", at = @At("HEAD"), argsOnly = true, ordinal = 4)
    private int ladsGhostFill(int color) {
        return ServerListDrag.ghostColor(color);
    }

    @ModifyVariable(method = "submitBlit", at = @At("HEAD"), argsOnly = true, ordinal = 4)
    private int ladsGhostBlit(int color) {
        return ServerListDrag.ghostColor(color);
    }
}
