package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.HudTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * HUD texture (HudTexture): Xaero's Minimap draws its in-world waypoints into the main target at the start of every GuiRenderer
 * render; the HUD renderer's pass is not the frame's GUI, so it skips that (else the waypoints would be drawn twice on build frames).
 */
@Pseudo
@Mixin(targets = "xaero.common.core.XaeroMinimapCore", remap = false)
public class XaeroMinimapHudPassMixin {
    @Inject(method = {"beforeIngameGuiRender", "afterIngameGuiRender"}, at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void lads$notInHudPass(CallbackInfo ci) {
        if (HudTexture.inHudPass) ci.cancel();
    }
}
