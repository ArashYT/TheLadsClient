package com.thelads.core.mixin;

import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Reserved for future Screen-level hooks.
 * The custom cursor overlay was removed — it referenced a missing texture
 * (thelads:textures/gui/cursor.png) which caused blank cursors and OBS
 * capture issues. The OS hardware cursor is used instead.
 */
@Mixin(Screen.class)
public class ScreenMixin {
    // intentionally empty
}
