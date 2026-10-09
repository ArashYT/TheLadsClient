package com.thelads.core.v1_20_1.feature.screenshots.mixin;

import com.thelads.core.v1_20_1.feature.screenshots.ScreenshotViewer1201;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Key mappings are not read while a screen is open, so the gallery key is matched here for the title and pause screens. */
@Mixin(KeyboardHandler.class)
public final class ScreenshotsKeyboardMixin {
    @Inject(method = "keyPress(JIIII)V", at = @At("HEAD"), cancellable = true)
    private void ladsGalleryKey(long window, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (!ScreenshotViewer1201.active() || action != 1 || window != client.getWindow().getWindow()) return;
        Screen screen = client.screen;
        if ((screen instanceof TitleScreen || screen instanceof PauseScreen)
            && ScreenshotViewer1201.getInstance().getOpenScreenshotsScreenKey().matches(key, scanCode)) {
            ScreenshotViewer1201.open(screen);
            ci.cancel();
        }
    }
}
