/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
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

@Mixin(value={Screen.class})
public abstract class ScreenBackgroundMixin {
    @Shadow
    protected Minecraft minecraft;

    @Shadow
    protected abstract void extractPanorama(GuiGraphicsExtractor var1, float var2);

    @Inject(method={"extractBackground"}, at={@At(value="HEAD")}, cancellable=true, require=0)
    private void ladsPanoramaOnlyMultiplayer(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!(this instanceof JoinMultiplayerScreen)) {
            return;
        }
        if (this.minecraft == null || this.minecraft.level != null) {
            return;
        }
        this.extractPanorama(g, partialTick);
        this.minecraft.gui.extractDeferredSubtitles();
        ci.cancel();
    }
}

