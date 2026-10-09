// Derived from quick-pack 1.4.0 by Drex (commit b80dac1, MIT); see META-INF/lads-sources/quickpack/LICENSE.
// Modified by The Lads: ported to Minecraft 1.20.1 from the Lads 26.3 embedded copy. 1.20.1's overlay has no isReadyToFadeOut;
// its render() starts the two-second fade when the reload is done (fadeOutStart is set), which is where it ends here.
package com.thelads.core.v1_20_1.embedded.quickpack.mixin;

import com.thelads.core.v1_20_1.embedded.quickpack.config.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// require = 0: under Forge the game's overlay is Forge's own subclass, which draws itself without LoadingOverlay.render, so this
// hook has nothing to do there (the title screen and pack indexing still apply).
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    private long fadeOutStart;

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void removeFadeOut(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        // render() has just finished the reload (fadeOutStart is set at that point, after the finish callback and the screen init).
        if (ConfigManager.config.removeLoadingOverlayFadeOut && this.fadeOutStart > -1L) {
            this.minecraft.setOverlay(null);
        }
    }
}
