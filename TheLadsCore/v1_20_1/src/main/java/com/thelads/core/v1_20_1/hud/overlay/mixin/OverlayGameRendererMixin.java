package com.thelads.core.v1_20_1.hud.overlay.mixin;

import com.thelads.core.v1_20_1.hud.overlay.OverlayFeatures1201;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Right after the HUD pass (vanilla and Forge both ask for the loading overlay next): the crosshair with F1 (Gui.render is skipped
 * then) and, under Forge's ForgeGui, the HUD that Gui.render's TAIL never got to draw.
 */
@Mixin(GameRenderer.class)
public class OverlayGameRendererMixin {
    @Inject(method = "render(FJZ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getOverlay()Lnet/minecraft/client/gui/screens/Overlay;", ordinal = 0))
    private void lads$afterHud(float partialTick, long nanoTime, boolean renderLevel, CallbackInfo callback) {
        OverlayFeatures1201.afterHud(renderLevel);
    }
}
