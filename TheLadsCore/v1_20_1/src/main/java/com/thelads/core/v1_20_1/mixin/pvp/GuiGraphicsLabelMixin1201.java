package com.thelads.core.v1_20_1.mixin.pvp;

import com.thelads.core.client.PvpVisuals;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Dark Mode: container labels (vanilla's dark gray) turn light while AbstractContainerScreenDarkMixin1201 marks them; both drawString ends. */
@Mixin(GuiGraphics.class)
public class GuiGraphicsLabelMixin1201 {
    @ModifyVariable(method = {
        "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I",
        "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)I"
    }, at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private int lads$labelColor(int color) {
        return PvpVisuals.label(color);
    }
}
