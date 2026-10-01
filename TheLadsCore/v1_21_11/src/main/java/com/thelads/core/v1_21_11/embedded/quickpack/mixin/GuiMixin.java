// Derived from quick-pack 1.4.0 by Drex (commit b80dac1, MIT); see META-INF/lads-sources/quickpack/LICENSE.
// On 1.21.x the first title screen is built in a Minecraft lambda instead of Gui.
package com.thelads.core.v1_21_11.embedded.quickpack.mixin;

import com.thelads.core.v1_21_11.embedded.quickpack.config.ConfigManager;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Minecraft.class)
public abstract class GuiMixin {
    @ModifyArg(
        method = "method_53528",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/TitleScreen;<init>(ZLnet/minecraft/client/gui/components/LogoRenderer;)V"
        ),
        index = 0
    )
    public boolean disableFadeIn(boolean fading) {
        if (ConfigManager.config.removeLoadingOverlayFadeOut) {
            return false;
        }
        return fading;
    }
}
