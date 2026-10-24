package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.GuiScaleOverride;
import com.thelads.core.v1_8_9.feature.Borderless189;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Borderless Fullscreen's window can have an extra row below the monitor: the GUI lays out in the visible rows (the hotbar stays whole).
 * GUI Scale (common GuiScaleOverride) lays the GUI out at its fractional scale; getScaleFactor() then rounds it up, for the few places
 * that multiply by a whole scale (Qol189.scale and Qol189.pixels give those the exact one).
 */
@Mixin(ScaledResolution.class)
public abstract class ScaledResolutionMixin {
    @Shadow private int scaledWidth;
    @Shadow private int scaledHeight;
    @Shadow private int scaleFactor;
    @Shadow @Final @Mutable private double scaledWidthD;
    @Shadow @Final @Mutable private double scaledHeightD;

    @Inject(method = "<init>(Lnet/minecraft/client/Minecraft;)V", at = @At("RETURN"), require = 1)
    private void ladsVisibleRows(Minecraft mc, CallbackInfo ci) {
        double scale = scaleFactor;
        if (GuiScaleOverride.on) {
            scale = GuiScaleOverride.scale(mc.displayWidth, mc.displayHeight);
            scaledWidthD = mc.displayWidth / scale;
            scaledHeightD = mc.displayHeight / scale;
            scaledWidth = MathHelper.ceiling_double_int(scaledWidthD);
            scaledHeight = MathHelper.ceiling_double_int(scaledHeightD);
            scaleFactor = (int) Math.ceil(scale);
        }
        if (Borderless189.extraRows() > 0)
            scaledHeight = MathHelper.ceiling_double_int((mc.displayHeight - Borderless189.extraRows()) / scale);
    }
}
