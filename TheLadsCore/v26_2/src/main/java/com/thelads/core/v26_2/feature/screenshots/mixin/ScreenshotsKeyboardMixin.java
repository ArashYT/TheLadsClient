package com.thelads.core.v26_2.feature.screenshots.mixin;
import com.thelads.core.v26_2.feature.screenshots.ScreenshotViewer;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.screens.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(KeyboardHandler.class)
public final class ScreenshotsKeyboardMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void ladsGalleryKey(long window, int action, KeyEvent event, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (!ScreenshotViewer.active() || action != 1 || window != client.getWindow().handle()) return;
        var screen = client.gui.screen();
        if ((screen instanceof TitleScreen || screen instanceof PauseScreen)
            && ScreenshotViewer.getInstance().getOpenScreenshotsScreenKey().matches(event)) {
            ScreenshotViewer.open(screen); ci.cancel();
        }
    }
}
