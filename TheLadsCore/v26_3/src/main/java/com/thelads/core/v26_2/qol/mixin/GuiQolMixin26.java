package com.thelads.core.v26_2.qol.mixin;

import com.thelads.core.client.KeepCursor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep Cursor Position: where the pointer was as a container screen closes (MouseHandlerQolMixin26 puts it back). */
@Mixin(Gui.class)
abstract class GuiQolMixin26 {
    @Shadow private Screen screen;

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void lads$containerClosing(Screen next, CallbackInfo ci) {
        if (screen instanceof net.minecraft.client.gui.screens.ConfirmLinkScreen && next != screen) {
            ConfirmLinkScreenMixin26.releaseActiveTexture();
        }
        if (!(screen instanceof AbstractContainerScreen<?>) || next == screen) return;
        Minecraft mc = Minecraft.getInstance();
        KeepCursor.closed(mc.mouseHandler.xpos(), mc.mouseHandler.ypos(), System.nanoTime());
    }
}
