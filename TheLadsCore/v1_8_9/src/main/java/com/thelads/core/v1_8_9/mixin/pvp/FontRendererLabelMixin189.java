package com.thelads.core.v1_8_9.mixin.pvp;

import com.thelads.core.client.PvpVisuals;
import net.minecraft.client.gui.FontRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Dark Mode: container labels (drawn in vanilla's dark gray) turn light while GuiContainerDarkMixin189 marks them. OptiFine: require = 0. */
@Mixin(FontRenderer.class)
public abstract class FontRendererLabelMixin189 {
    @ModifyVariable(method = "drawString(Ljava/lang/String;FFIZ)I", at = @At("HEAD"), argsOnly = true, require = 0)
    private int ladsLabelColor(int color) {
        return PvpVisuals.label(color);
    }
}
