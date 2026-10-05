package com.thelads.core.v26_2.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.thelads.core.v26_2.feature.HudCapture;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * HUD FPS cap (HudCapture): while a capped HUD build runs, the frame time steps are the time since the last build, so HUD animations
 * that advance by them (Jade's fade, mods' HUD animations) keep their pace instead of moving one frame's worth per build.
 */
@Mixin(DeltaTracker.Timer.class)
public class DeltaTrackerCapMixin {
    @ModifyReturnValue(method = "getGameTimeDeltaTicks", at = @At("RETURN"), require = 1)
    private float lads$gameStep(float frame) {
        return HudCapture.gameStep(frame);
    }

    @ModifyReturnValue(method = "getRealtimeDeltaTicks", at = @At("RETURN"), require = 1)
    private float lads$realStep(float frame) {
        return HudCapture.realStep(frame);
    }
}
