package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.Borderless189;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Borderless Fullscreen's window can have an extra row below the monitor: the GUI lays out in the visible rows (the hotbar stays whole). */
@Mixin(ScaledResolution.class)
public abstract class ScaledResolutionMixin {
    @Shadow private int scaledHeight;
    @Shadow private int scaleFactor;

    @Inject(method = "<init>(Lnet/minecraft/client/Minecraft;)V", at = @At("RETURN"), require = 1)
    private void ladsVisibleRows(Minecraft mc, CallbackInfo ci) {
        if (Borderless189.extraRows() > 0)
            scaledHeight = MathHelper.ceiling_double_int((double) (mc.displayHeight - Borderless189.extraRows()) / scaleFactor);
    }
}
