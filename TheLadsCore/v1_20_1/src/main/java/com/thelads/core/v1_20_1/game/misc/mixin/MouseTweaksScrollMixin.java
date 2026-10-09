package com.thelads.core.v1_20_1.game.misc.mixin;

import com.thelads.core.v1_20_1.game.misc.NativeMouseTweaks1201;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lads Mouse Tweaks' wheel. Container screens that scroll something of their own (the creative tabs, trades, the loom and the
 * stonecutter) override mouseScrolled without calling their parent, so the wheel is taken here, just before the open screen sees it.
 */
@Mixin(MouseHandler.class)
public abstract class MouseTweaksScrollMixin {
    @Shadow private Minecraft minecraft;
    @Shadow private double xpos, ypos;

    @Inject(method = "onScroll", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;mouseScrolled(DDD)Z"),
        cancellable = true, require = 1)
    private void lads$mouseTweaksScroll(long window, double xOffset, double yOffset, CallbackInfo callback) {
        if (!(minecraft.screen instanceof AbstractContainerScreen<?> screen)) return;
        // The same numbers the game hands the screen.
        double amount = (minecraft.options.discreteMouseScroll().get() ? Math.signum(yOffset) : yOffset) * minecraft.options.mouseWheelSensitivity().get();
        double x = xpos * minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getScreenWidth();
        double y = ypos * minecraft.getWindow().getGuiScaledHeight() / minecraft.getWindow().getScreenHeight();
        if (NativeMouseTweaks1201.scroll(screen, x, y, amount)) {
            screen.afterMouseAction();
            callback.cancel();
        }
    }
}
