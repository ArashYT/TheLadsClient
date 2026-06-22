package com.thelads.core.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Multiplayer screen: skip the blur + dark menu background layers so the
 * raw panorama shows through behind the server list.
 */
@Mixin(Screen.class)
public abstract class ScreenBackgroundMixin {

    @Shadow protected Minecraft minecraft;

    @Shadow protected abstract void extractPanorama(GuiGraphicsExtractor g, float partialTick);

    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void ladsPanoramaOnlyMultiplayer(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!((Object) this instanceof JoinMultiplayerScreen)) return;
        if (this.minecraft == null || this.minecraft.level != null) return;
        this.extractPanorama(g, partialTick);
        this.minecraft.gui.hud.extractDeferredSubtitles();
        ci.cancel();
    }
}
