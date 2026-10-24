package com.thelads.core.v1_8_9.qol.mixin;

import com.thelads.core.client.GuiScaleOverride;
import com.thelads.core.v1_8_9.qol.Qol189;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiEnchantment;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** GUI Scale: the enchanting table's book gets its viewport at the exact (fractional) scale, not ScaledResolution's whole one. */
@Mixin(GuiEnchantment.class)
public abstract class GuiEnchantmentQolMixin189 {
    @Redirect(method = "drawGuiContainerBackgroundLayer", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/GlStateManager;viewport(IIII)V", ordinal = 0))
    private void ladsBookViewport(int x, int y, int width, int height) {
        if (!GuiScaleOverride.on) {
            GlStateManager.viewport(x, y, width, height);
            return;
        }
        ScaledResolution resolution = new ScaledResolution(Minecraft.getMinecraft());
        double scale = Qol189.scale(resolution);
        GlStateManager.viewport((int) ((resolution.getScaledWidth() - 320) / 2 * scale), (int) ((resolution.getScaledHeight() - 240) / 2 * scale),
            (int) (320 * scale), (int) (240 * scale));
    }
}
