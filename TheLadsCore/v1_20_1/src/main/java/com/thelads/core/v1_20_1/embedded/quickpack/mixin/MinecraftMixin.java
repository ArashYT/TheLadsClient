// Derived from quick-pack 1.4.0 by Drex (commit b80dac1, MIT); see META-INF/lads-sources/quickpack/LICENSE.
// Modified by The Lads: ported to Minecraft 1.20.1 from the Lads 26.3 embedded copy (26.3 patches the same title screen call
// from Gui.buildInitialScreens; in 1.20.1 it is made in Minecraft.setInitialScreen).
package com.thelads.core.v1_20_1.embedded.quickpack.mixin;

import com.thelads.core.v1_20_1.embedded.quickpack.config.ConfigManager;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    // require = 0: Forge patches the game's start-up around this call; without it the title screen simply fades in as before.
    @ModifyArg(
        method = "setInitialScreen",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/TitleScreen;<init>(Z)V"),
        index = 0,
        require = 0
    )
    public boolean disableFadeIn(boolean fading) {
        if (ConfigManager.config.removeLoadingOverlayFadeOut) {
            return false;
        }
        return fading;
    }
}
