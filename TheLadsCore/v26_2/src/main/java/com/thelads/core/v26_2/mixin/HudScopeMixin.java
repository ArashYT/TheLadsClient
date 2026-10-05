package com.thelads.core.v26_2.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.v26_2.feature.HudCapture;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * HUD FPS cap (HudCapture): vanilla's spyglass zoom eases a share of the way per frame (lerp by half the frame's tick step). A capped
 * build takes one such step for each frame since the last build, so the zoom-in takes as long as with the cap off.
 */
@Mixin(Hud.class)
public class HudScopeMixin {
    @WrapOperation(method = "extractCameraOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;lerp(FFF)F", ordinal = 0), require = 1)
    private float lads$scopeSteps(float share, float from, float to, Operation<Float> lerp) {
        int frames = HudCapture.catchUpFrames();
        float sum = HudCapture.gameStep(0);
        if (frames <= 1 || sum <= 0) return lerp.call(share, from, to);
        float perTick = share / sum; // vanilla: 0.5 of the way per tick of frame time
        for (int frame = 0; frame < frames; frame++) from = lerp.call(perTick * HudCapture.frameStep(frame), from, to);
        return from;
    }
}
