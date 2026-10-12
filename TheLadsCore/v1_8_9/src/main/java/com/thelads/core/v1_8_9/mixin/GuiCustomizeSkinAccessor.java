package com.thelads.core.v1_8_9.mixin;

import net.minecraft.client.gui.GuiCustomizeSkin;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The Skin Customization screen (SkinCustomizationScreen189): where vanilla's goes back to. */
@Mixin(GuiCustomizeSkin.class)
public interface GuiCustomizeSkinAccessor {
    @Accessor GuiScreen getParentScreen();
}
