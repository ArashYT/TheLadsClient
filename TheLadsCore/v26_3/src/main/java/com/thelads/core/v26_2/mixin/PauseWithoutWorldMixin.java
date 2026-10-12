package com.thelads.core.v26_2.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * A pause menu with no world to return to (a screen chain from the pause menu's server list after a join left the world, e.g.
 * Discover Servers' Back) is the title screen, as vanilla makes no screen without a world the title screen.
 */
@Mixin(Gui.class)
abstract class PauseWithoutWorldMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true, require = 1)
    private Screen lads$noPauseWithoutWorld(Screen screen) {
        return screen instanceof PauseScreen && Minecraft.getInstance().level == null ? new TitleScreen() : screen;
    }
}
