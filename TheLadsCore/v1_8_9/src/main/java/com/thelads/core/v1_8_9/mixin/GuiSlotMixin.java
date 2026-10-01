package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.gui.LadsPalette;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.renderer.Tessellator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lists (worlds, servers, packs, controls) on Lads panels instead of the dirt texture, as GuiScreenMixin does behind menus. */
@Mixin(GuiSlot.class)
public abstract class GuiSlotMixin {
    @Shadow public int width, top, bottom, right, left;

    @Inject(method = "drawContainerBackground", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void ladsListBackground(Tessellator tessellator, CallbackInfo ci) {
        Gui.drawRect(left, top, right, bottom, LadsPalette.PANEL);
        ci.cancel();
    }

    /** The header and footer bands. */
    @Inject(method = "overlayBackground", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsBand(int startY, int endY, int startAlpha, int endAlpha, CallbackInfo ci) {
        Gui.drawRect(left, startY, left + width, endY, (startAlpha << 24) | (LadsPalette.BACKGROUND & 0xFFFFFF));
        Gui.drawRect(left, startY == 0 ? endY - 1 : startY, left + width, startY == 0 ? endY : startY + 1, LadsPalette.BORDER);
        ci.cancel();
    }
}
