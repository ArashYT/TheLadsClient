package com.thelads.core.v26_2.mixin.pvp;

import com.thelads.core.client.PvpVisuals;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Dark Mode: container labels (vanilla's dark gray) turn light while AbstractContainerScreenDarkMixin26 marks them; every text() ends here. */
@Mixin(GuiGraphicsExtractor.class)
public class GuiGraphicsLabelMixin26 {
    @ModifyVariable(method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V", at = @At("HEAD"), argsOnly = true,
        ordinal = 2)
    private int lads$labelColor(int color) {
        return PvpVisuals.label(color);
    }
}
