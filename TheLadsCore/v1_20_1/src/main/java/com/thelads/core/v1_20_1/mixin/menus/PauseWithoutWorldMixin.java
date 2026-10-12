package com.thelads.core.v1_20_1.mixin.menus;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * A pause menu with no world to return to (a screen chain from the pause menu's server list after a join left the world, e.g.
 * Discover Servers' Back) is the title screen, as vanilla makes no screen without a world the title screen.
 */
@Mixin(Minecraft.class)
abstract class PauseWithoutWorldMixin {
    @Shadow public net.minecraft.client.multiplayer.ClientLevel level;

    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true, require = 1)
    private Screen lads$noPauseWithoutWorld(Screen screen) {
        return screen instanceof PauseScreen && level == null ? new TitleScreen() : screen;
    }
}
