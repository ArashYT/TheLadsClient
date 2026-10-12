package com.thelads.core.v1_8_9.mixin;

import net.minecraft.client.gui.GuiMainMenu;
import org.lwjgl.opengl.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Pauses title screen panorama rotation while the window is unfocused (Display.isActive() is false),
 * eliminating background scene updates while focused behavior remains pixel-identical.
 */
@Mixin(GuiMainMenu.class)
public abstract class GuiMainMenuMixin {
    @ModifyConstant(method = "updateScreen", constant = @Constant(intValue = 1), require = 1)
    private int ladsPanoramaIncrement(int one) {
        return Display.isActive() ? 1 : 0;
    }
}
